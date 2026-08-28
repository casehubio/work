/*
 * Copyright 2026-Present The Case Hub Authors
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
package io.casehub.work.engine;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CallerRefTest {

  // --- PlanItemCallerRef encode/parse round-trip ---

  @Test
  void planItemCallerRef_encode_hasExpectedFormat() {
    final UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    final String encoded = PlanItemRef.encode(caseId, "plan-item-abc");

    assertThat(encoded).isEqualTo("case:00000000-0000-0000-0000-000000000001/pi:plan-item-abc");
  }

  @Test
  void planItemCallerRef_parse_roundTrip() {
    final UUID caseId = UUID.randomUUID();
    final String encoded = PlanItemRef.encode(caseId, "item-99");

    final CallerRef parsed = CallerRef.parse(encoded);

    assertThat(parsed).isInstanceOf(PlanItemRef.class);
    final PlanItemRef pi = (PlanItemRef) parsed;
    assertThat(pi.caseId()).isEqualTo(caseId);
    assertThat(pi.planItemId()).isEqualTo("item-99");
  }

  @Test
  void planItemCallerRef_caseId_isAccessibleViaInterface() {
    final UUID caseId = UUID.randomUUID();
    final CallerRef ref = CallerRef.parse(PlanItemRef.encode(caseId, "pi-1"));

    assertThat(ref.caseId()).isEqualTo(caseId);
  }

  // --- GateCallerRef encode/parse round-trip ---

  @Test
  void gateCallerRef_encode_hasExpectedFormat() {
    final UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    final String encoded = GateRef.encode(caseId, 42L);

    assertThat(encoded).isEqualTo("case:00000000-0000-0000-0000-000000000002/gate:42");
  }

  @Test
  void gateCallerRef_parse_roundTrip() {
    final UUID caseId = UUID.randomUUID();
    final long gateId = 9_876_543L;
    final String encoded = GateRef.encode(caseId, gateId);

    final CallerRef parsed = CallerRef.parse(encoded);

    assertThat(parsed).isInstanceOf(GateRef.class);
    final GateRef gate = (GateRef) parsed;
    assertThat(gate.caseId()).isEqualTo(caseId);
    assertThat(gate.gateId()).isEqualTo(gateId);
  }

  @Test
  void gateCallerRef_caseId_isAccessibleViaInterface() {
    final UUID caseId = UUID.randomUUID();
    final CallerRef ref = CallerRef.parse(GateRef.encode(caseId, 1L));

    assertThat(ref.caseId()).isEqualTo(caseId);
  }

  // --- Discrimination: gate vs plan-item ---

  @Test
  void parse_piFormat_isNotGateCallerRef() {
    final CallerRef ref = CallerRef.parse(PlanItemRef.encode(UUID.randomUUID(), "x"));

    assertThat(ref).isNotInstanceOf(GateRef.class);
  }

  @Test
  void parse_gateFormat_isNotPlanItemCallerRef() {
    final CallerRef ref = CallerRef.parse(GateRef.encode(UUID.randomUUID(), 5L));

    assertThat(ref).isNotInstanceOf(PlanItemRef.class);
  }

  // --- Null and invalid input ---

  @Test
  void parse_null_returnsNull() {
    assertThat(CallerRef.parse(null)).isNull();
  }

  @Test
  void parse_emptyString_returnsNull() {
    assertThat(CallerRef.parse("")).isNull();
  }

  @Test
  void parse_randomString_returnsNull() {
    assertThat(CallerRef.parse("not-a-caller-ref")).isNull();
  }

  @Test
  void parse_malformedUuid_returnsNull() {
    assertThat(CallerRef.parse("case:not-a-uuid/pi:item-1")).isNull();
  }

  @Test
  void parse_gateWithNonNumericId_returnsNull() {
    assertThat(CallerRef.parse("case:00000000-0000-0000-0000-000000000001/gate:not-a-number"))
        .isNull();
  }

  @Test
  void parse_unknownSegmentType_returnsNull() {
    assertThat(CallerRef.parse("case:00000000-0000-0000-0000-000000000001/unknown:value")).isNull();
  }

  // --- GateId precision ---

  @Test
  void gateCallerRef_gateId_preservesLargeValue() {
    final long largeId = Long.MAX_VALUE;
    final UUID caseId = UUID.randomUUID();
    final CallerRef ref = CallerRef.parse(GateRef.encode(caseId, largeId));

    assertThat(((GateRef) ref).gateId()).isEqualTo(largeId);
  }

    @Test
    void planItemRef_implementsCrossSystemRef() {
        UUID        caseId = UUID.randomUUID();
        PlanItemRef ref    = new PlanItemRef(caseId, "pi-1");
        assertThat(ref.system()).isEqualTo("engine");
        assertThat(ref.encode()).isEqualTo(PlanItemRef.encode(caseId, "pi-1"));
        assertThat(ref).isInstanceOf(io.casehub.work.api.CrossSystemRef.class);
    }

    @Test
    void gateRef_implementsCrossSystemRef() {
        UUID    caseId = UUID.randomUUID();
        GateRef ref    = new GateRef(caseId, 42L);
        assertThat(ref.system()).isEqualTo("engine");
        assertThat(ref.encode()).isEqualTo(GateRef.encode(caseId, 42L));
        assertThat(ref).isInstanceOf(io.casehub.work.api.CrossSystemRef.class);
    }
}
