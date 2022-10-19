package org.eclipse.jgit.dircache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BeforeEachDemo {

	@BeforeEach
	void beforeEach_1() {
		System.out.println("\nInside beforeEach_1");
	}

	@BeforeEach
	void beforeEach_2() {
		System.out.println("Inside beforeEach_2");
	}

	@AfterEach
	void afterEach_1() {
		System.out.println("\nInside afterEach_1");
	}

	@AfterEach
	void afterEach_2() {
		System.out.println("Inside afterEach_2");
	}

	@Test
	public void test1() {
		System.out.println("Inside test1");
	}

	@Test
	public void test2() {
		System.out.println("Inside test2");
	}
}
