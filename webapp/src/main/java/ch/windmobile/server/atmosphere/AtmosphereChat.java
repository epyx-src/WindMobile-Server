/*******************************************************************************
 * Copyright (c) 2011 epyx SA.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package ch.windmobile.server.atmosphere;

import java.net.HttpURLConnection;
import java.security.MessageDigest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.atmosphere.annotation.Broadcast;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.jersey.Broadcastable;
import org.atmosphere.jersey.SuspendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import ch.windmobile.server.datasourcemodel.DataSourceException;
import ch.windmobile.server.datasourcemodel.xml.Error;
import ch.windmobile.server.resource.ExceptionHandler;
import ch.windmobile.server.security.SecurityHelper;
import ch.windmobile.server.security.WindMobileAuthenticationProvider;
import ch.windmobile.server.social.mongodb.MongoDBServiceLocator;
import ch.windmobile.server.socialmodel.ChatService;
import ch.windmobile.server.socialmodel.ServiceLocator;
import ch.windmobile.server.socialmodel.UserService;
import ch.windmobile.server.socialmodel.xml.Message;
import ch.windmobile.server.socialmodel.xml.User;

@Path("/{chatRoomId}")
public class AtmosphereChat {
    protected static final Logger log = LoggerFactory.getLogger(AtmosphereLogger.class);

    // @TODO : Read Jersey doc about field lifecycle in resources
    private ServiceLocator serviceLocator = new MongoDBServiceLocator();

    private @PathParam("chatRoomId")
    Broadcaster chatRoom;

    @Context
    HttpServletRequest servletRequest;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SuspendResponse<String> subscribe() {
        return new SuspendResponse.SuspendResponseBuilder<String>().broadcaster(chatRoom).addListener(new AtmosphereLogger()).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Broadcast
    public Broadcastable postMessage(@FormParam("message") String text, @FormParam("emailHash") String emailHash) {
        try {
            String email;
            String pseudo;

            ChatService chatService = serviceLocator.connect(null).getService(ChatService.class);
            if (SecurityHelper.hasRole(WindMobileAuthenticationProvider.roleUser) == false) {
                if (chatService.allowAnonymousMessages(chatRoom.getID()) == false) {
                    Error error = new Error();
                    error.setCode(DataSourceException.Error.UNAUTHORIZED.getCode());
                    error.setMessage("Chat room '" + chatRoom + "' does not allow anonymous message");
                    throw new WebApplicationException(Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).entity(error).build());
                }

                String ip = servletRequest.getRemoteAddr();
                email = "anonymous@" + ip;
                pseudo = "@" + ip;
            } else {
                email = SecurityContextHolder.getContext().getAuthentication().getName();
                emailHash = null;
                UserService userService = serviceLocator.getService(UserService.class);
                User user = userService.findByEmail(email);
                pseudo = user.getPseudo();
            }

            if ((emailHash == null) || (emailHash.equals(""))) {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] emailHashBytes = md.digest(email.trim().toLowerCase().getBytes());
                StringBuffer hexBuffer = new StringBuffer();
                for (int i = 0; i < emailHashBytes.length; i++) {
                    String hex = Integer.toHexString(0xFF & emailHashBytes[i]);
                    if (hex.length() == 1) {
                        hexBuffer.append("0" + hex);
                    } else {
                        hexBuffer.append(hex);
                    }
                }
                emailHash = hexBuffer.toString();
            }

            Message message2 = chatService.postMessage(chatRoom.getID(), pseudo, text, emailHash);
            return new Broadcastable(message2, "", chatRoom);
        } catch (Exception e) {
            ExceptionHandler.treatException(e);
            return null;
        }
    }
}