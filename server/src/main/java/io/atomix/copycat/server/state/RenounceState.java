package io.atomix.copycat.server.state;

import java.util.concurrent.CompletableFuture;

import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.request.LeaveRequest;

/**
 * Renounce state.
 * <p>
 * A leader wishing to leave the cluster enters this state to commit a cluster configuration
 * update for removing self from the list of active members.
 */
public class RenounceState extends LeaderState {

  public RenounceState(ServerState context) {
    super(context);
  }

  @Override
  public CopycatServer.State type() {
    return CopycatServer.State.RENOUNCE;
  }

  @Override
  public synchronized CompletableFuture<AbstractState> open() {
    return super.open()
        .thenRun(this::leave)
        .thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> close() {
    return super.close();
  }

  /**
   * Starts leaving the cluster.
   */
  private CompletableFuture<Void> leave() {
    return super.leave(LeaveRequest.builder().withMember(context.getAddress()).build()).thenRun(() -> transition(CopycatServer.State.INACTIVE));
  }
}