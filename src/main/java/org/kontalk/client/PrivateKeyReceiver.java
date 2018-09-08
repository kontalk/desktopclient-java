/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.ExceptionCallback;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqregister.packet.Registration;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.kontalk.misc.Callback;

/**
 * Send request and listen to response for private data over
 * 'jabber:iq:register' namespace.
 *
 * Temporary server connection is established when requesting key.
 *
 * TODO not used
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class PrivateKeyReceiver implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(PrivateKeyReceiver.class.getName());

    private static final String FORM_TYPE_VALUE = "http://kontalk.org/protocol/register#privatekey";
    private static final String FORM_TOKEN_VAR = "token";

    private final Callback.Handler<String> mHandler;
    private KonConnection mConn = null;

    public PrivateKeyReceiver(Callback.Handler<String> handler) {
        mHandler = handler;
    }

    public void sendRequest(EndpointServer server, boolean validateCertificate,
            final String registrationToken) {
        // create connection
        mConn = new KonConnection(server, validateCertificate);

        Thread thread = new Thread("Private Key Request") {
            @Override
            public void run() {
                PrivateKeyReceiver.this.sendRequestAsync(registrationToken);
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void sendRequestAsync(String registrationToken) {
        // connect
        try {
            mConn.connect();
        } catch (XMPPException | SmackException | IOException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "can't connect to "+mConn.getServer(), ex);
            mHandler.handle(new Callback<>(ex));
            return;
        }

        Registration iq = new Registration();
        iq.setType(IQ.Type.set);
        iq.setTo(mConn.getXMPPServiceDomain());
        Form form = new Form(DataForm.Type.submit);

        // form type field
        FormField type = new FormField(FormField.FORM_TYPE);
        type.setType(FormField.Type.hidden);
        type.addValue(FORM_TYPE_VALUE);
        form.addField(type);

        // token field
        FormField fieldKey = new FormField(FORM_TOKEN_VAR);
        fieldKey.setLabel("Registration token");
        fieldKey.setType(FormField.Type.text_single);
        fieldKey.addValue(registrationToken);
        form.addField(fieldKey);

        iq.addExtension(form.getDataFormToSend());

        try {
            mConn.sendIqWithResponseCallback(iq, this, new ExceptionCallback() {
                @Override
                public void processException(Exception exception) {
                    mHandler.handle(new Callback<>(exception));
                }
            });
        } catch (SmackException.NotConnectedException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "not connected", ex);
            mHandler.handle(new Callback<>(ex));
        }
    }

    @Override
    public void processStanza(Stanza packet) {
        LOGGER.info("response: "+packet);

        mConn.removeSyncStanzaListener(this);
        mConn.disconnect();

        if (!(packet instanceof IQ)) {
            LOGGER.warning("response not an IQ packet");
            finish(null);
            return;
        }
        IQ iq = (IQ) packet;

        if (iq.getType() != IQ.Type.result) {
            LOGGER.warning("ignoring response with IQ type: "+iq.getType());
            this.finish(null);
            return;
        }

        DataForm response = iq.getExtension(DataForm.ELEMENT, DataForm.NAMESPACE);
        if (response == null) {
            this.finish(null);
            return;
        }

        String token = null;
        List<FormField> fields = response.getFields();
        for (FormField field : fields) {
            if ("token".equals(field.getVariable())) {
                token = field.getValues().get(0).toString();
                break;
            }
        }

        this.finish(token);
    }

    private void finish(String token) {
        mHandler.handle(token == null ?
                new Callback<>() :
                new Callback<>(token));
    }

}
