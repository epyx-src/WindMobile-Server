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
package ch.windmobile.server.resource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.windmobile.server.datasourcemodel.DataSourceException;
import ch.windmobile.server.datasourcemodel.xml.Error;

public class ExceptionHandler {
    protected static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);

    private static String printStacktrace(Throwable e) {
        if (e.getStackTrace() != null) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            return writer.toString();
        }
        return "";
    }

    private static void logError(Throwable e) {
        if (e instanceof WebApplicationException) {
            WebApplicationException web = (WebApplicationException) e;
            log.error("WindMobile WebApplicationException(" + web.getResponse().getStatus() + "):", e);
        } else {
            log.error("WindMobile Exception:", e);
        }
    }

    public static void treatException(Throwable e) throws WebApplicationException {
        logError(e);

        if (e instanceof WebApplicationException) {
            throw (WebApplicationException) e;
        } else if (e instanceof DataSourceException) {
            DataSourceException dataSourceException = (DataSourceException) e;

            Error error = new Error();
            error.setCode(dataSourceException.getError().getCode());
            if (dataSourceException.getMessage() != null) {
                error.setMessage(dataSourceException.getMessage());
            } else if (dataSourceException.getCause() != null) {
                error.setMessage(dataSourceException.getCause().getMessage());
            }
            error.setStacktrace(printStacktrace(dataSourceException));

            Status httpStatus;
            switch (dataSourceException.getError()) {
            case CONNECTION_ERROR:
                httpStatus = Status.SERVICE_UNAVAILABLE;
                break;

            case INVALID_DATA:
                httpStatus = Status.BAD_REQUEST;
                break;

            case DATABASE_ERROR:
                httpStatus = Status.INTERNAL_SERVER_ERROR;
                break;

            default:
                httpStatus = Status.INTERNAL_SERVER_ERROR;
                break;
            }

            throw new WebApplicationException(Response.status(httpStatus).entity(error).build());
        } else {
            Error error = new Error();
            error.setCode(DataSourceException.Error.SERVER_ERROR.getCode());
            if (e.getMessage() != null) {
                error.setMessage(e.getMessage());
            } else if (e.getCause() != null) {
                error.setMessage(e.getCause().getMessage());
            }
            error.setStacktrace(printStacktrace(e));

            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }
    }
}
