/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.saga.alpha.server.tcc.service;

import static com.seanyinx.github.unit.scaffolding.Randomness.uniquify;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Optional;
import org.apache.servicecomb.saga.alpha.server.tcc.TccApplication;
import org.apache.servicecomb.saga.alpha.server.tcc.callback.OmegaCallbacksRegistry;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.GlobalTxEvent;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.GlobalTxEventRepository;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.ParticipatedEvent;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.ParticipatedEventRepository;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.TccTxEvent;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.TccTxEventDBRepository;
import org.apache.servicecomb.saga.alpha.server.tcc.jpa.TccTxType;
import org.apache.servicecomb.saga.common.TransactionStatus;
import org.apache.servicecomb.saga.pack.contract.grpc.GrpcServiceConfig;
import org.apache.servicecomb.saga.pack.contract.grpc.GrpcTccCoordinateCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TccApplication.class})
public class TccTxEventServiceTransactionTest {

  @Autowired
  private TccTxEventService tccTxEventService;

  @MockBean
  private TccTxEventDBRepository tccTxEventDBRepository;

  @Autowired
  private ParticipatedEventRepository participatedEventRepository;

  @Autowired
  private GlobalTxEventRepository globalTxEventRepository;

  private final String globalTxId = uniquify("globalTxId");
  private final String localTxId = uniquify("localTxId");
  private final String parentTxId = uniquify("parentTxId");
  private final String confirmMethod = "confirm";
  private final String cancelMethod = "cancel";
  private final String serviceName = uniquify("serviceName");
  private final String instanceId = uniquify("instanceId");

  private GlobalTxEvent tccStartEvent;
  private ParticipatedEvent participatedEvent;
  private GlobalTxEvent tccEndEvent;
  private TccTxEvent coordinateEvent;

  @Before
  public void setup() {
    tccStartEvent = new GlobalTxEvent(serviceName, instanceId, globalTxId,
        localTxId, parentTxId, TccTxType.STARTED.name(), TransactionStatus.Succeed.name());

    participatedEvent = new ParticipatedEvent(serviceName, instanceId, globalTxId, localTxId,
        parentTxId, confirmMethod, cancelMethod, TransactionStatus.Succeed.name());

    tccEndEvent = new GlobalTxEvent(serviceName, instanceId, globalTxId,
        localTxId, parentTxId, TccTxType.ENDED.name(), TransactionStatus.Succeed.name());

    coordinateEvent = new TccTxEvent(serviceName, instanceId, globalTxId,
        localTxId, parentTxId, TccTxType.COORDINATED.name(), TransactionStatus.Succeed.name());
  }

  @After
  public void teardown() {
  }

  @Test
  public void rollbackAfterSaveTccTxEventDbFailure() {
    doThrow(NullPointerException.class).when(tccTxEventDBRepository).save((TccTxEvent) any());

    tccTxEventService.onTccStartedEvent(tccStartEvent);
    Optional<List<GlobalTxEvent>> startEvents = globalTxEventRepository.findByGlobalTxId(globalTxId);
    assertThat(startEvents.isPresent(), is(false));

    tccTxEventService.onParticipatedEvent(participatedEvent);
    Optional<List<ParticipatedEvent>> participates = participatedEventRepository.findByGlobalTxId(globalTxId);
    assertThat(participates.isPresent(), is(false));

    tccTxEventService.onTccEndedEvent(tccEndEvent);
    Optional<List<GlobalTxEvent>> endEvents = globalTxEventRepository.findByGlobalTxId(globalTxId);
    assertThat(endEvents.isPresent(), is(false));

    participatedEventRepository.save(participatedEvent);
    tccTxEventService.onCoordinatedEvent(coordinateEvent);
    participates = participatedEventRepository.findByGlobalTxId(globalTxId);
    assertThat(participates.isPresent(), is(true));
  }
}
