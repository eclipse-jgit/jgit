package org.eclipse.jgit.transport;

import com.bigbrassband.common.apache.B3HttpClientConnection;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnection;

import java.io.IOException;

class NTLM extends HttpAuthMethod {
    private String username;
    private String password;
    private String domain;

    public NTLM() {
        super(Type.NTLM);
    }

    @Override
    void authorize(final String username, final String password) {
        // supported username formats: https://msdn.microsoft.com/en-us/library/windows/desktop/aa380525(v=vs.85).aspx
        // DOMAIN\UserName
        this.username = username;
        this.password = password;
        String[] res = username.split("(\\\\|%5[Cc])");
        if (res.length > 1) {
            this.domain = res[0];
            this.username = res[1];
        }
    }

    @Override
    void configureRequest(HttpConnection conn) throws IOException {
        if (conn instanceof B3HttpClientConnection) {
            B3HttpClientConnection b3HttpClientConnection = (B3HttpClientConnection) conn;
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new NTCredentials(username, password, null, domain));
            b3HttpClientConnection.setCredentialsProvider(credentialsProvider);
        }
    }
}
