/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.jgit.api.errors;

/**
 * Exception thrown when a symbolic ref command was called with not a symbolic ref
 */
public class NotSymbolicRefException  extends GitAPIException {
    private static final long serialVersionUID = 1L;

    public NotSymbolicRefException(String message) {
        super(message);
    }

    public NotSymbolicRefException(String message, Throwable cause) {
        super(message, cause);
    }
    
    
}
