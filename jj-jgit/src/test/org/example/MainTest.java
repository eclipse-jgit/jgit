package org.example;

import org.junit.Test;

import static org.junit.Assert.*;

public class MainTest {

  @Test
  public void testConstant4() {
    assertEquals("constant function is 4", 4, Main.constant4());
  }
}