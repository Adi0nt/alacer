package net.adiont.alacer.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FeedTargetTest {
	@Test
	void startsAtTwoPerWorkerAndFollowsWhatTheWorkersStarted() {
		FeedTarget feed = new FeedTarget(2);
		assertEquals(4, feed.target());
		feed.beforeClear(3); // nothing was fed: no estimate
		assertEquals(4, feed.target());
		feed.afterFeed(4);
		feed.beforeClear(1); // three started, one left over
		assertEquals(5, feed.target());
		feed.afterFeed(5);
		feed.beforeClear(5); // none started
		assertEquals(4, feed.target(), "never below two per worker");
	}

	@Test
	void anEmptiedQueueDoublesTheDepthUpToTheCap() {
		FeedTarget feed = new FeedTarget(2);
		int[] expected = {8, 16, 32, 64, 64};
		for (int want : expected) {
			feed.afterFeed(feed.target());
			feed.beforeClear(0);
			assertEquals(want, feed.target());
		}
		feed.afterFeed(64);
		feed.beforeClear(50);
		assertEquals(16, feed.target(), "14 started plus one per worker");
	}

	@Test
	void anUnfedFrameLeavesTheTargetAloneAndWorkerCountsAreClamped() {
		FeedTarget feed = new FeedTarget(3);
		feed.afterFeed(6);
		feed.afterFeed(-1); // the option was switched off this frame
		feed.beforeClear(0);
		assertEquals(6, feed.target());
		assertEquals(2, new FeedTarget(0).target());
		assertEquals(FeedTarget.MAX, new FeedTarget(100).target());
	}
}
