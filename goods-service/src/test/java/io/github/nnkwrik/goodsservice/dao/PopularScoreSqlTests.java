package io.github.nnkwrik.goodsservice.dao;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PopularScoreSqlTests {

    private static final String EXPECTED_POPULAR_SCORE =
            "(1 * browse_count + 10 * want_count + 500) * " +
                    "exp(-greatest(timestampdiff(second, last_edit, now()), 0) / 864000.0)";

    @Test
    public void calculatesPopularityFromElapsedSeconds() {
        assertEquals(EXPECTED_POPULAR_SCORE, GoodsMapper.popular_score);
        assertEquals(EXPECTED_POPULAR_SCORE, SearchMapper.popular_score);
    }
}
