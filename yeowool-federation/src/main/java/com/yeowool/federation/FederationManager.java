package com.yeowool.federation;

import com.yeowool.federation.database.FederationRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All business logic for federations, backed directly by {@link FederationRepository}
 * (no in-memory cache — federations are edited rarely enough, and read
 * rarely enough per-command, that a straight DB round trip per call is
 * simpler than keeping a cache consistent across 3 servers sharing one DB,
 * the way {@code yeowool-raid}'s RaidManager has to for its much hotter
 * session state). Every method here does blocking JDBC I/O — call off the
 * main thread only.
 */
public final class FederationManager {

    private static final int DESCRIPTION_MAX_LENGTH = 255;

    private final FederationRepository repository;

    public FederationManager(FederationRepository repository) {
        this.repository = repository;
    }

    public enum CreateResult { SUCCESS, LAND_ALREADY_IN_FEDERATION, NAME_TAKEN }

    public CreateResult create(UUID leaderLandId, String federationName) throws SQLException {
        if (repository.findMemberByLandId(leaderLandId).isPresent()) {
            return CreateResult.LAND_ALREADY_IN_FEDERATION;
        }
        if (repository.findByName(federationName).isPresent()) {
            return CreateResult.NAME_TAKEN;
        }
        UUID federationId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        repository.insert(new Federation(federationId, federationName, null, 1, leaderLandId, now));
        repository.insertMember(new FederationMember(federationId, leaderLandId, FederationRole.LEADER, now));
        return CreateResult.SUCCESS;
    }

    public enum ApplyResult { SUCCESS, FEDERATION_NOT_FOUND, ALREADY_MEMBER, ALREADY_APPLIED }

    public ApplyResult applyToJoin(UUID applicantLandId, String federationName) throws SQLException {
        Optional<Federation> federation = repository.findByName(federationName);
        if (federation.isEmpty()) {
            return ApplyResult.FEDERATION_NOT_FOUND;
        }
        if (repository.findMemberByLandId(applicantLandId).isPresent()) {
            return ApplyResult.ALREADY_MEMBER;
        }
        UUID federationId = federation.get().id();
        if (repository.applicationExists(federationId, applicantLandId)) {
            return ApplyResult.ALREADY_APPLIED;
        }
        repository.insertApplication(federationId, applicantLandId, System.currentTimeMillis());
        return ApplyResult.SUCCESS;
    }

    /** @throws IllegalStateException if actingLandId isn't a member, or is a plain member with no approval rights. */
    public List<UUID> listApplicants(UUID actingLandId) throws SQLException {
        FederationMember actingMember = requireApprover(actingLandId);
        return repository.loadApplicantLandIds(actingMember.federationId());
    }

    public enum ApprovalResult { SUCCESS, NOT_AUTHORIZED, APPLICATION_NOT_FOUND }

    public ApprovalResult approve(UUID actingLandId, UUID applicantLandId) throws SQLException {
        Optional<FederationMember> actingMember = repository.findMemberByLandId(actingLandId);
        if (actingMember.isEmpty() || !FederationRules.canApprove(actingMember.get().role())) {
            return ApprovalResult.NOT_AUTHORIZED;
        }
        UUID federationId = actingMember.get().federationId();
        if (!repository.applicationExists(federationId, applicantLandId)) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }
        repository.deleteAllApplicationsForLand(applicantLandId);
        repository.insertMember(new FederationMember(federationId, applicantLandId, FederationRole.MEMBER, System.currentTimeMillis()));
        return ApprovalResult.SUCCESS;
    }

    public ApprovalResult reject(UUID actingLandId, UUID applicantLandId) throws SQLException {
        Optional<FederationMember> actingMember = repository.findMemberByLandId(actingLandId);
        if (actingMember.isEmpty() || !FederationRules.canApprove(actingMember.get().role())) {
            return ApprovalResult.NOT_AUTHORIZED;
        }
        UUID federationId = actingMember.get().federationId();
        if (!repository.applicationExists(federationId, applicantLandId)) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }
        repository.deleteApplication(federationId, applicantLandId);
        return ApprovalResult.SUCCESS;
    }

    public enum LeaveResult { SUCCESS, NOT_A_MEMBER, LEADER_MUST_TRANSFER_FIRST }

    public LeaveResult leave(UUID landId) throws SQLException {
        Optional<FederationMember> member = repository.findMemberByLandId(landId);
        if (member.isEmpty()) {
            return LeaveResult.NOT_A_MEMBER;
        }
        if (member.get().role() == FederationRole.LEADER) {
            return LeaveResult.LEADER_MUST_TRANSFER_FIRST;
        }
        repository.deleteMember(landId);
        return LeaveResult.SUCCESS;
    }

    public enum KickResult { SUCCESS, NOT_AUTHORIZED, TARGET_NOT_MEMBER, TARGET_NOT_KICKABLE }

    public KickResult kick(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<FederationMember> actingMember = repository.findMemberByLandId(actingLandId);
        if (actingMember.isEmpty()) {
            return KickResult.NOT_AUTHORIZED;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(actingMember.get().federationId())) {
            return KickResult.TARGET_NOT_MEMBER;
        }
        if (!FederationRules.canKick(actingMember.get().role(), targetMember.get().role())) {
            return KickResult.TARGET_NOT_KICKABLE;
        }
        repository.deleteMember(targetLandId);
        return KickResult.SUCCESS;
    }

    public enum AppointResult { SUCCESS, NOT_LEADER, TARGET_NOT_MEMBER, ALREADY_DEPUTY, CAP_REACHED }

    public AppointResult appointDeputy(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return AppointResult.NOT_LEADER;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(federation.get().id())) {
            return AppointResult.TARGET_NOT_MEMBER;
        }
        if (targetMember.get().role() == FederationRole.DEPUTY) {
            return AppointResult.ALREADY_DEPUTY;
        }
        long currentDeputyCount = repository.loadMembers(federation.get().id()).stream()
                .filter(m -> m.role() == FederationRole.DEPUTY)
                .count();
        if (currentDeputyCount >= FederationRules.deputyCap(federation.get().level())) {
            return AppointResult.CAP_REACHED;
        }
        repository.updateMemberRole(targetLandId, FederationRole.DEPUTY);
        return AppointResult.SUCCESS;
    }

    public enum DismissResult { SUCCESS, NOT_LEADER, TARGET_NOT_DEPUTY }

    public DismissResult dismissDeputy(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return DismissResult.NOT_LEADER;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(federation.get().id())
                || targetMember.get().role() != FederationRole.DEPUTY) {
            return DismissResult.TARGET_NOT_DEPUTY;
        }
        repository.updateMemberRole(targetLandId, FederationRole.MEMBER);
        return DismissResult.SUCCESS;
    }

    public enum TransferResult { SUCCESS, NOT_LEADER, TARGET_NOT_MEMBER }

    public TransferResult transferLeadership(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<Federation> federationOpt = requireLeaderFederation(actingLandId);
        if (federationOpt.isEmpty()) {
            return TransferResult.NOT_LEADER;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(federationOpt.get().id())) {
            return TransferResult.TARGET_NOT_MEMBER;
        }
        repository.updateMemberRole(actingLandId, FederationRole.MEMBER);
        repository.updateMemberRole(targetLandId, FederationRole.LEADER);
        repository.update(federationOpt.get().withLeaderLandId(targetLandId));
        return TransferResult.SUCCESS;
    }

    public enum DisbandResult { SUCCESS, NOT_LEADER }

    public DisbandResult disband(UUID actingLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return DisbandResult.NOT_LEADER;
        }
        repository.delete(federation.get().id());
        return DisbandResult.SUCCESS;
    }

    public enum DescriptionResult { SUCCESS, NOT_LEADER, TOO_LONG }

    public DescriptionResult updateDescription(UUID actingLandId, String newDescription) throws SQLException {
        if (newDescription.length() > DESCRIPTION_MAX_LENGTH) {
            return DescriptionResult.TOO_LONG;
        }
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return DescriptionResult.NOT_LEADER;
        }
        repository.update(federation.get().withDescription(newDescription));
        return DescriptionResult.SUCCESS;
    }

    public Optional<Federation> findByName(String name) throws SQLException {
        return repository.findByName(name);
    }

    public Optional<Federation> findByLandId(UUID landId) throws SQLException {
        Optional<FederationMember> member = repository.findMemberByLandId(landId);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return repository.findById(member.get().federationId());
    }

    public List<Federation> listAll() throws SQLException {
        return repository.loadAll();
    }

    public List<FederationMember> membersOf(UUID federationId) throws SQLException {
        return repository.loadMembers(federationId);
    }

    private FederationMember requireApprover(UUID actingLandId) throws SQLException {
        FederationMember member = repository.findMemberByLandId(actingLandId)
                .orElseThrow(() -> new IllegalStateException("not a member of any federation"));
        if (!FederationRules.canApprove(member.role())) {
            throw new IllegalStateException("no approval rights");
        }
        return member;
    }

    private Optional<Federation> requireLeaderFederation(UUID actingLandId) throws SQLException {
        Optional<FederationMember> member = repository.findMemberByLandId(actingLandId);
        if (member.isEmpty() || member.get().role() != FederationRole.LEADER) {
            return Optional.empty();
        }
        return repository.findById(member.get().federationId());
    }
}
