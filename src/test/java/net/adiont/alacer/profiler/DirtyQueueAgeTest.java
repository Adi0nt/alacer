package net.adiont.alacer.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class DirtyQueueAgeTest {
	@Test
	void repeatedSamplesKeepTheFirstObservationAndSchedulingClearsIt() {
		DirtyQueueAge age = new DirtyQueueAge();
		assertEquals(0, age.observe(10, 100, 0, 16, 32));
		assertEquals(50, age.observe(11, 150, 0, 16, 32));
		assertEquals(80, age.scheduled(11, 180, 0, 16, 32));
		assertEquals(-1, age.scheduled(11, 190, 0, 16, 32));
		assertEquals(0, age.observe(12, 200, 0, 16, 32));
	}

	@Test
	void repositionsMissingFramesAndUnobservedSchedulingDoNotReuseOldAges() {
		DirtyQueueAge age = new DirtyQueueAge();
		assertEquals(-1, age.scheduled(1, 10, 0, 0, 0));
		age.observe(1, 100, 0, 0, 0);
		assertEquals(0, age.observe(2, 200, 16, 0, 0));
		assertEquals(0, age.observe(4, 300, 16, 0, 0));
		assertEquals(-1, age.scheduled(5, 400, 16, 0, 0));
		age.observe(6, 500, 16, 0, 0);
		assertEquals(-1, age.scheduled(6, 600, 32, 0, 0));
	}
}
