package com.cunzhi.governance.insight.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ModuleInsights {

    private ModuleInsights() {
    }

    public record Breakdown(String key, long count) {
    }

    public record UserInsight(
            long total,
            long enabled,
            long disabled,
            long locked,
            long loggedInLast30Days,
            List<Breakdown> roles
    ) {
        public UserInsight {
            roles = List.copyOf(roles);
        }
    }

    public record GridInsight(
            long communityCount,
            long gridCount,
            long enabledGridCount,
            long disabledGridCount,
            long assignedGridCount,
            long unassignedGridCount,
            long geoReadyGridCount,
            List<CommunityNode> communities
    ) {
        public GridInsight {
            communities = List.copyOf(communities);
        }
    }

    public record CommunityNode(
            String id,
            String code,
            String name,
            String status,
            List<GridNode> grids
    ) {
        public CommunityNode {
            grids = List.copyOf(grids);
        }
    }

    public record GridNode(
            String id,
            String code,
            String name,
            String status,
            String address,
            BigDecimal centerLongitude,
            BigDecimal centerLatitude,
            boolean geoReady,
            List<WorkerNode> workers
    ) {
        public GridNode {
            workers = List.copyOf(workers);
        }
    }

    public record WorkerNode(String id, String name, boolean primary) {
    }

    public record ResidentInsight(
            long residentCount,
            long householdCount,
            long active,
            long moved,
            long deceased,
            long archived,
            long keyPopulationCount,
            List<Breakdown> specialGroups
    ) {
        public ResidentInsight {
            specialGroups = List.copyOf(specialGroups);
        }
    }

    public record EventInsight(
            long total,
            long actionable,
            List<Breakdown> statuses,
            List<Breakdown> severities
    ) {
        public EventInsight {
            statuses = List.copyOf(statuses);
            severities = List.copyOf(severities);
        }
    }

    public record TaskInsight(
            long total,
            long overdue,
            List<Breakdown> statuses,
            List<Breakdown> priorities
    ) {
        public TaskInsight {
            statuses = List.copyOf(statuses);
            priorities = List.copyOf(priorities);
        }
    }
}
