package com.oxygenxml.git.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.Policy;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import junit.framework.TestCase;

/********************************************************************************
 * Copyright (c) {year} Contributors to the Eclipse Foundation 1
 *
 * See the NOTICE file(s) distributed with this work for additional 2
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the 3
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 4
 ********************************************************************************/
public class SecurityManagerMissingPermissionsTest extends TestCase {
  
  private StringWriter errorOutputWriter = new StringWriter(); 
  
  @Override
  protected void setUp() throws Exception {
    refreshPolicy(Policy.getPolicy());
    System.setSecurityManager(new SecurityManager());
    
    Appender appender = new WriterAppender(
        new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN), errorOutputWriter);
    
    org.apache.log4j.Logger.getRootLogger().addAppender(appender);
    
    super.setUp();
  }
  
  /**
   * If a SecurityManager is active a lot of {@link java.io.FilePermission} are thrown while initializing a repository.
   * @throws Exception
   */
  @Test
  public void testCreateNewRepos_MissingPermissions() throws Exception {
    File wcTree = new File("target/gen/CreateNewRepositoryTest_testCreateNewRepos");
    
    Git git = null;
    try {
      git = Git.init().setBare(false).setDirectory(new File(wcTree.getAbsolutePath())).call();
    } finally {
      if (git != null) {
        git.close();
      }
      
      FileUtils.deleteDirectory(wcTree);
    }
    
    assertEquals("", errorOutputWriter.toString());
  }
  
  /**
   * Refresh the Java Security Policy.
   *  
   * @param policy The policy object.
   * 
   * @throws IOException If the temporary file that contains the policy could not be created.
   */
  private static void refreshPolicy(Policy policy) throws IOException {
    // Starting with an all permissions policy.
    String policyString = "grant { permission java.security.AllPermission; };";
    
    // Do not use TemporaryFilesFactory, it will create a dependency cycle 
    File policyFile = File.createTempFile("oxy_policy", ".txt");
    
    try {
      //Write the policy
      try (OutputStream fos = java.nio.file.Files.newOutputStream(policyFile.toPath())) {
        fos.write(policyString.getBytes("UTF-8"));
      }
      
      System.setProperty("java.security.policy", policyFile.toURI().toURL().toString());
      //Refresh the policy
      policy.refresh();
    } finally {
      try {
        java.nio.file.Files.delete(policyFile.toPath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

}
