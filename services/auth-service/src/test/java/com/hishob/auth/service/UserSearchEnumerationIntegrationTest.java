package com.hishob.auth.service;

import com.hishob.auth.dto.UserSearchResponse;
import com.hishob.auth.entity.User;
import com.hishob.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-10: the search field was pasted straight into a LIKE pattern, so a single "%" listed every
 * account in the directory. These tests run the real JPQL against the project's configured test
 * database and keep both halves of the promise: wildcards from users are inert, legitimate
 * case-insensitive name and email search still works.
 */
@SpringBootTest
@Transactional
class UserSearchEnumerationIntegrationTest {

    private static final String SEARCHER_EMAIL = "searcher@enumeration.test";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipService friendshipService;

    private User searcher;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        searcher = userRepository.saveAndFlush(new User("Searcher One", SEARCHER_EMAIL, "not-a-real-hash"));
        seed("Alice Name", "alice@enumeration.test");
        seed("Alan Turing", "alan@enumeration.test");
        seed("An_Up", "underscore@enumeration.test");
        seed("AnXUp", "anxup@enumeration.test");
        seed("50% Off", "discount@enumeration.test");
        seed("Fifty Off", "fifty@enumeration.test");
        seed("Ab!cd", "bang@enumeration.test");
        seed("Abcd", "nobang@enumeration.test");
        seed("Percent User", "100%@enumeration.test");
    }

    private User seed(String name, String email) {
        return userRepository.saveAndFlush(new User(name, email, "not-a-real-hash"));
    }

    private List<String> search(String query) {
        return friendshipService.searchUsers(SEARCHER_EMAIL, query).stream()
                .map(UserSearchResponse::getName)
                .toList();
    }

    @Test
    @DisplayName("Legitimate substring search by name and email is preserved, case-insensitively")
    void legitimateSearch_StillWorks() {
        assertThat(search("alice")).containsExactly("Alice Name");
        assertThat(search("ALAN")).containsExactly("Alan Turing");
        assertThat(search("nam")).containsExactly("Alice Name");
        assertThat(search("discount@")).containsExactly("50% Off");
        assertThat(search("enumeration.test")).hasSize(9);
        assertThat(search("  Ali  ")).containsExactly("Alice Name");
    }

    @Test
    @DisplayName("The searcher itself is never returned")
    void searcherIsExcluded() {
        assertThat(search("Searcher One")).isEmpty();
        assertThat(search("searcher@enumeration.test")).isEmpty();
    }

    @Test
    @DisplayName("A lone percent sign no longer dumps the directory")
    void percentIsLiteral() {
        assertThat(search("%")).containsExactly("50% Off", "Percent User");
        assertThat(search("50%")).containsExactly("50% Off");
        assertThat(search("% off")).containsExactly("50% Off");
        assertThat(search("50% of")).containsExactly("50% Off");
        assertThat(search("100%")).containsExactly("Percent User");
    }

    @Test
    @DisplayName("An underscore matches only an underscore, never one arbitrary character")
    void underscoreIsLiteral() {
        assertThat(search("_")).containsExactly("An_Up");
        assertThat(search("an_")).containsExactly("An_Up");
        assertThat(search("_u")).containsExactly("An_Up");
        assertThat(search("anxup")).containsExactly("AnXUp");
    }

    @Test
    @DisplayName("The escape character itself is matched literally, in the right order")
    void escapeCharacterIsLiteral() {
        assertThat(search("!")).containsExactly("Ab!cd");
        assertThat(search("b!c")).containsExactly("Ab!cd");
        assertThat(search("!%")).isEmpty();
        assertThat(search("%!")).isEmpty();

        assertThat(FriendshipService.escapeLikePattern("!")).isEqualTo("!!");
        assertThat(FriendshipService.escapeLikePattern("%")).isEqualTo("!%");
        assertThat(FriendshipService.escapeLikePattern("_")).isEqualTo("!_");
        // '!' is doubled before the others, otherwise the '!' characters just inserted would
        // escape the following replacement and "%" would survive as a wildcard
        assertThat(FriendshipService.escapeLikePattern("%!_")).isEqualTo("!%!!!_");
    }

    @Test
    @DisplayName("Blank and null queries keep returning nothing instead of everything")
    void blankQueryReturnsNothing() {
        assertThat(search("")).isEmpty();
        assertThat(search("   ")).isEmpty();
        assertThat(friendshipService.searchUsers(SEARCHER_EMAIL, null)).isEmpty();
    }

    @Test
    @DisplayName("Result window stays at 20 rows ordered by name, and paging still works")
    void paginationAndOrderingArePreserved() {
        for (int i = 0; i < 25; i++) {
            seed("Bulk " + String.format("%02d", i), "bulk" + i + "@enumeration.test");
        }

        List<String> page = search("Bulk");

        assertThat(page).hasSize(20);
        assertThat(page).isSorted();
        assertThat(page.getFirst()).isEqualTo("Bulk 00");
        assertThat(page.getLast()).isEqualTo("Bulk 19");

        List<User> secondSlice = userRepository.searchUsers(
                FriendshipService.escapeLikePattern("Bulk"), searcher.getId(), PageRequest.of(1, 5));
        assertThat(secondSlice).hasSize(5);
        assertThat(secondSlice.getFirst().getName()).isEqualTo("Bulk 05");
    }

    @Test
    @DisplayName("Control: unescaped wildcards really do enumerate, so the escaping is load-bearing")
    void unescapedPatternWouldEnumerate() {
        List<User> everything = userRepository.searchUsers(
                "%", searcher.getId(), PageRequest.of(0, 500));
        List<User> anySingleChar = userRepository.searchUsers(
                "_", searcher.getId(), PageRequest.of(0, 500));
        List<User> oneCharWildcardName = userRepository.searchUsers(
                "_up", searcher.getId(), PageRequest.of(0, 500));

        assertThat(everything).hasSize(9);
        assertThat(anySingleChar).hasSize(9);
        assertThat(oneCharWildcardName).extracting(User::getName)
                .containsExactlyInAnyOrder("An_Up", "AnXUp");

        assertThat(search("%")).hasSizeLessThan(everything.size());
        assertThat(search("_")).hasSizeLessThan(anySingleChar.size());
        assertThat(search("_up")).containsExactly("An_Up");
    }

    @Test
    @DisplayName("Search results carry identity only, never contact details")
    void resultsNeverExposeEmail() {
        List<UserSearchResponse> results = friendshipService.searchUsers(SEARCHER_EMAIL, "alice");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUserId()).isNotNull();
        assertThat(results.getFirst().getName()).isEqualTo("Alice Name");
        assertThat(results.getFirst()).hasNoNullFieldsOrProperties();
    }
}
