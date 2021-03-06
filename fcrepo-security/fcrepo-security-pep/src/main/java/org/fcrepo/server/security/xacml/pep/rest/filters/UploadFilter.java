/* The contents of this file are subject to the license and copyright terms
 * detailed in the license directory at the root of the source tree (also
 * available online at http://fedora-commons.org/license/).
 */
package org.fcrepo.server.security.xacml.pep.rest.filters;

import java.io.IOException;

import java.net.URI;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.xacml.attr.AnyURIAttribute;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.ctx.RequestCtx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fcrepo.common.Constants;

import org.fcrepo.server.security.xacml.pep.PEPException;
import org.fcrepo.server.security.xacml.util.LogUtil;

/**
* Filter for Upload
*
* @author Stephen Bayliss
* @version $$Id$$
*/
public class UploadFilter
        extends AbstractFilter {

    private static final Logger logger =
        LoggerFactory.getLogger(UploadFilter.class);

    /**
     * Default constructor.
     *
     * @throws PEPException
     */
    public UploadFilter()
    throws PEPException {
        super();
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fcrepo.server.security.xacml.pep.rest.filters.RESTFilter#handleRequest(javax.servlet
     * .http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public RequestCtx handleRequest(HttpServletRequest request,
                                    HttpServletResponse response)
    throws IOException, ServletException {

        RequestCtx req = null;

        Map<URI, AttributeValue> actions = new HashMap<URI, AttributeValue>();
        Map<URI, AttributeValue> resAttr = new HashMap<URI, AttributeValue>();

        try {

            String pid = "FedoraRepository";

            resAttr.put(Constants.OBJECT.PID.getURI(),
                        new StringAttribute(pid));
            resAttr
            .put(new URI("urn:oasis:names:tc:xacml:1.0:resource:resource-id"),
                 new AnyURIAttribute(new URI(pid)));

            // note - no API specified in legacy XACML, so none specified here
            /*
            actions.put(Constants.ACTION.API.getURI(),
                        new StringAttribute(Constants.ACTION.APIA.getURI()
                                            .toASCIIString()));
            */
            actions.put(Constants.ACTION.ID.getURI(),
                        new StringAttribute(Constants.ACTION.UPLOAD
                                            .getURI().toASCIIString()));

            req =
                getContextHandler().buildRequest(getSubjects(request),
                                                 actions,
                                                 resAttr,
                                                 getEnvironment(request));

            LogUtil.statLog(request.getRemoteUser(),
                            Constants.ACTION.LIST_METHODS.getURI()
                            .toASCIIString(),
                            pid,
                            null);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ServletException(e);
        }

        return req;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.fcrepo.server.security.xacml.pep.rest.filters.RESTFilter#handleResponse(javax.servlet
     * .http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public RequestCtx handleResponse(HttpServletRequest request,
                                     HttpServletResponse response)
    throws IOException, ServletException {
        return null;
    }

}
