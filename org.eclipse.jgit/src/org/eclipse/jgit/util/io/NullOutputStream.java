package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.OutputStream;


public class NullOutputStream extends OutputStream {
    public final static NullOutputStream INSTANCE=new NullOutputStream();

    @Override
    public void write(int i) throws IOException { } // NO-OP

    @Override
    public void write(byte[] b,int off, int len) throws IOException { } //NO-OP
}