package com.yeowool.community.vote;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * The only class touching NuVotifier — registered only when the {@code Votifier} plugin is enabled
 * (the lobby), so community still loads on servers without it. {@link VotifierEvent} is a sync
 * event (NuVotifier fires it on the main thread).
 */
public final class VotifierListener implements Listener {

    private final VoteService service;

    public VotifierListener(VoteService service) {
        this.service = service;
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        String site = vote.getServiceName();
        service.recordVote(vote.getUsername(), site == null || site.isBlank() ? "unknown" : site);
    }
}
