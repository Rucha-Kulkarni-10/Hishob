package com.hishob.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard / friends / expense-details layout guard.
 *
 * Group expenses and individual (1-on-1) expenses are separate accounting systems, and the
 * surfaces where mixing them is most visible are the dashboard and the friends page. These
 * assertions keep the agreed shape: no overall expense total on the dashboard, one labelled
 * overall total plus a "View Details" link at the top of the friends page, the per-scope
 * breakdown only on the details page, and no group data reachable from a group page's personal
 * services (or vice versa). There is no JavaScript test runner in this repository, so this
 * source-level check is what fails when the layout regresses.
 */
class DashboardExpenseSeparationGuardTest {

    private static final String WEB = "frontend/hishob-web/src";
    private static final String DASHBOARD_PAGE = WEB + "/pages/dashboard/DashboardPage.tsx";
    private static final String FRIENDS_PAGE = WEB + "/pages/friends/FriendsPage.tsx";
    private static final String OVERVIEW_PAGE = WEB + "/pages/dashboard/ExpenseOverviewPage.tsx";
    private static final String APP_ROUTES = WEB + "/App.tsx";

    @Test
    @DisplayName("the dashboard keeps the overall expense total out of it entirely")
    void dashboardShowsNoExpenseTotal() throws IOException {
        String dashboard = read(DASHBOARD_PAGE);

        assertThat(dashboard)
                .as("the overall total and its details link live on the friends page now")
                .doesNotContain("Total Recorded Expenses")
                .doesNotContain("Total recorded expenses")
                .doesNotContain("/expense-overview")
                .doesNotContain("View Details");
        assertThat(dashboard)
                .as("the dashboard must not call the account-wide overview endpoint at all")
                .doesNotContain("getDashboardOverview")
                .doesNotContain("DashboardOverviewResponse");
        assertThat(dashboard)
                .as("recorded spend, share and paid are different metrics and must not appear on the dashboard")
                .doesNotContain("recordedSpend")
                .doesNotContain("yourShare")
                .doesNotContain("youPaid");
    }

    @Test
    @DisplayName("the friends page shows one labelled overall total and a View Details link, never a per-scope breakdown")
    void friendsPageShowsSingleTotalWithDetailsLink() throws IOException {
        String friends = read(FRIENDS_PAGE);

        assertThat(friends)
                .as("the overall total must be labelled as recorded spend")
                .contains("Total Recorded Expenses");
        assertThat(friends)
                .as("the total is the combined (group + individual) scope, counted once")
                .contains("overview.combined.recordedSpend");
        assertThat(friends)
                .as("'View Details' must link to the expense overview page")
                .contains("to=\"/expense-overview\"")
                .contains("View Details");
        assertThat(friends)
                .as("the breakdown belongs on the details page, not here")
                .doesNotContain("overview.group")
                .doesNotContain("overview.personal")
                .doesNotContain("CombinedOverviewCards");
        assertThat(friends)
                .as("recorded spend, share and paid are different metrics")
                .doesNotContain("yourShare")
                .doesNotContain("youPaid");
        assertThat(friends.indexOf("Total Recorded Expenses"))
                .as("the overall total must sit above the friend-related content")
                .isLessThan(friends.indexOf("aria-label=\"Friends summary\""));
    }

    @Test
    @DisplayName("the removed three-card overview component is gone, not just unreferenced")
    void perScopeDashboardCardsAreDeleted() {
        assertThat(repoRoot().resolve(WEB).resolve("components/dashboard/CombinedOverviewCards.tsx"))
                .as("the breakdown cards must not come back as an unused component")
                .doesNotExist();
    }

    @Test
    @DisplayName("the details page keeps group and individual expenses in separate labelled sections")
    void detailsPageSeparatesTheTwoScopes() throws IOException {
        String page = read(OVERVIEW_PAGE);

        assertThat(page)
                .as("section headings must be individually labelled")
                .contains("aria-label=\"Group expenses\"")
                .contains("aria-label=\"Individual expenses\"");
        assertThat(page)
                .as("each scope is totalled from its own totals")
                .contains("scope={overview.group}")
                .contains("scope={overview.personal}");
        assertThat(page)
                .as("the overall total is the combined scope, and the two parts are shown next to it")
                .contains("overview.combined.recordedSpend")
                .contains("overview.group.recordedSpend")
                .contains("overview.personal.recordedSpend");
        assertThat(page)
                .as("group detail navigation must point at the group's own pages")
                .contains("/groups/${g.id}/expenses")
                .contains("/groups/${g.id}/balances");
    }

    @Test
    @DisplayName("the details page is routed inside the authenticated route block")
    void detailsPageIsRouted() throws IOException {
        String routes = read(APP_ROUTES);

        assertThat(routes)
                .as("/expense-overview must lazy-load the details page")
                .contains("ExpenseOverviewPage")
                .contains("path=\"/expense-overview\"");
        assertThat(routes.indexOf("<Route element={<ProtectedRoute />}>"))
                .as("the route must sit inside the authenticated route block")
                .isLessThan(routes.indexOf("path=\"/expense-overview\""));
    }

    @Test
    @DisplayName("group pages never reach friend services, and friend pages never reach group services")
    void financialSystemsStayApartInPages() throws IOException {
        assertThat(offenders(repoRoot().resolve(WEB).resolve("pages/groups"),
                List.of("@/services/personalBalanceService", "@/services/personalExpenseService", "@/services/friendService")))
                .as("group pages must not load friend balances or individual expenses")
                .isEmpty();

        List<Path> personalPages = List.of(
                repoRoot().resolve(WEB).resolve("pages/personal"),
                repoRoot().resolve(WEB).resolve("pages/friends"));
        List<Path> sources = new java.util.ArrayList<>();
        for (Path directory : personalPages) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(p -> p.toString().endsWith(".tsx")).forEach(sources::add);
            }
        }
        // The account-wide overview endpoint is deliberately allowed here: it is what feeds the
        // friends-page total. Anything that reads a *group's* own data is not.
        assertThat(offendersOn(sources,
                List.of("@/services/balanceService", "@/services/groupService",
                        "getGroupExpenses", "createExpense")))
                .as("individual and friend pages must not read group balances or group expenses")
                .isEmpty();
    }

    private List<String> offenders(Path directory, List<String> needles) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return offendersOn(paths.filter(p -> p.toString().endsWith(".tsx")).toList(), needles);
        }
    }

    private List<String> offendersOn(List<Path> files, List<String> needles) throws IOException {
        List<String> found = new java.util.ArrayList<>();
        for (Path file : files) {
            String content = Files.readString(file);
            for (String needle : needles) {
                if (content.contains(needle)) {
                    found.add(repoRoot().relativize(file) + " -> " + needle);
                }
            }
        }
        return found;
    }

    private String read(String relativePath) throws IOException {
        Path path = repoRoot().resolve(relativePath);
        assertThat(path).as(relativePath + " must exist").exists();
        return Files.readString(path);
    }

    private Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("render.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Repository root not found from " + Path.of("").toAbsolutePath());
    }
}
