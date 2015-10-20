/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.copycat.server.state;

import io.atomix.copycat.client.response.Response;
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.request.LeaveRequest;
import io.atomix.copycat.server.response.LeaveResponse;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.util.concurrent.Scheduled;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Leave state.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
final class LeaveState extends ActiveState {
  private Scheduled leaveFuture;

  public LeaveState(ServerState context) {
    super(context);
  }

  @Override
  public CompletableFuture<AbstractState> open() {
    return super.open()
      .thenRun(this::startLeaveTimeout)
      .thenRun(this::leave)
      .thenApply(v -> this);
  }

  @Override
  public CopycatServer.State type() {
    return CopycatServer.State.LEAVE;
  }

  /**
   * Sets a leave timeout.
   */
  private void startLeaveTimeout() {
    leaveFuture = context.getThreadContext().schedule(context.getElectionTimeout(), () -> {
      if (isOpen()) {
        LOGGER.warn("{} - Failed to leave the cluster in {}", context.getAddress(), context.getElectionTimeout());
        transition(CopycatServer.State.INACTIVE);
      }
    });
  }

  /**
   * Starts leaving the cluster.
   */
  private void leave() {
    if (context.getLeader() != null) {
      leave(context.getLeader(), context.getCluster().getActiveMembers().iterator());
    } else {
      Iterator<MemberState> iterator = context.getCluster().getActiveMembers().iterator();
      if (iterator.hasNext()) {
        leave(iterator.next().getAddress(), iterator);
      } else {
        LOGGER.debug("{} - Failed to leave the cluster", context.getAddress());
        transition(CopycatServer.State.INACTIVE);
      }
    }
  }

  /**
   * Recursively attempts to leave the cluster.
   */
  private void leave(Address member, Iterator<MemberState> iterator) {
    LOGGER.debug("{} - Attempting to leave via {}", context.getAddress(), member);

    context.getConnections().getConnection(member).thenAccept(connection -> {
      if (isOpen()) {
        LeaveRequest request = LeaveRequest.builder()
          .withMember(context.getAddress())
          .build();
        connection.<LeaveRequest, LeaveResponse>send(request).whenComplete((response, error) -> {
          if (isOpen()) {
            if (error == null) {
              if (response.status() == Response.Status.OK) {
                LOGGER.info("{} - Successfully left via {}", context.getAddress(), member);
                transition(CopycatServer.State.INACTIVE);
              } else {
                LOGGER.debug("{} - Failed to leave {}", context.getAddress(), member);
                if (iterator.hasNext()) {
                  leave(iterator.next().getAddress(), iterator);
                } else {
                  transition(CopycatServer.State.INACTIVE);
                }
              }
            } else {
              LOGGER.debug("{} - Failed to leave {}", context.getAddress(), member);
              if (iterator.hasNext()) {
                leave(iterator.next().getAddress(), iterator);
              } else {
                transition(CopycatServer.State.INACTIVE);
              }
            }
          }
        });
      }
    });
  }

  /**
   * Cancels the leave timeout.
   */
  private void cancelLeaveTimer() {
    if (leaveFuture != null) {
      LOGGER.debug("{} - Cancelling leave timeout", context.getAddress());
      leaveFuture.cancel();
      leaveFuture = null;
    }
  }

  @Override
  public CompletableFuture<Void> close() {
    return super.close().thenRun(this::cancelLeaveTimer);
  }

}
