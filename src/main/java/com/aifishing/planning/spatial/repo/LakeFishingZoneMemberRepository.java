package com.aifishing.planning.spatial.repo;

import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishingZoneMemberRepository extends JpaRepository<LakeFishingZoneMember, LakeFishingZoneMemberId> {

    List<LakeFishingZoneMember> findByZoneIdOrderBySequenceAsc(UUID zoneId);

    List<LakeFishingZoneMember> findByZoneIdInOrderByZoneIdAscSequenceAsc(List<UUID> zoneIds);
}
