/* The contents of this file are subject to the license and copyright terms
 * detailed in the license directory at the root of the source tree (also
 * available online at http://fedora-commons.org/license/).
 */
package org.fcrepo.server.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.fcrepo.common.Constants;
import org.fcrepo.common.FaultException;
import org.fcrepo.server.errors.GeneralException;
import org.fcrepo.server.errors.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xacml.AbstractPolicy;
import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.PolicySet;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.attr.BagAttribute;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.combine.PolicyCombiningAlgorithm;
import com.sun.xacml.cond.EvaluationResult;
import com.sun.xacml.ctx.Status;
import com.sun.xacml.finder.PolicyFinder;
import com.sun.xacml.finder.PolicyFinderResult;

/**
 * XACML PolicyFinder for Fedora.
 * <p>
 * This provides repository-wide policies and object-specific policies,
 * when available.
 */
public class PolicyFinderModule
        extends com.sun.xacml.finder.PolicyFinderModule {

    private static final Logger logger =
            LoggerFactory.getLogger(PolicyFinderModule.class);

    private static final List<String> ERROR_CODE_LIST = new ArrayList<String>(1);

    static {
        ERROR_CODE_LIST.add(Status.STATUS_PROCESSING_ERROR);
    }

    private final String m_combiningAlgorithm;

//    private final RepositoryReader m_repoReader;

    private final boolean m_validateRepositoryPolicies;

    private final boolean m_validateObjectPoliciesFromDatastream;

    private final PolicyParser m_policyParser;

    private final PolicyStrategy m_policyStrategy;

    private final Collection<AbstractPolicy> m_repositoryPolicies;

    public PolicyFinderModule(String combiningAlgorithm,
                              String repositoryPolicyDirectoryPath,
                              String repositoryBackendPolicyDirectoryPath,
                              String repositoryPolicyGuiToolDirectoryPath,
                              boolean validateRepositoryPolicies,
                              boolean validateObjectPoliciesFromDatastream,
                              PolicyParser policyParser,
                              PolicyStrategy policyStrategy)
            throws GeneralException {

        m_combiningAlgorithm = combiningAlgorithm;
//        m_repoReader = repoReader;
        m_validateRepositoryPolicies = validateRepositoryPolicies;
        m_validateObjectPoliciesFromDatastream = validateObjectPoliciesFromDatastream;
        m_policyParser = policyParser;
        m_policyStrategy = policyStrategy;
        logger.info("Loading repository policies...");
        try {
            Map<String,AbstractPolicy> repositoryPolicies =
                    m_policyStrategy.loadPolicies(m_policyParser,
                    m_validateRepositoryPolicies,
                    new File(repositoryBackendPolicyDirectoryPath));
            repositoryPolicies.putAll(
                    m_policyStrategy.loadPolicies(m_policyParser,
                                 m_validateRepositoryPolicies,
                                 new File(repositoryPolicyDirectoryPath)));
            m_repositoryPolicies = repositoryPolicies.values();
        } catch (Exception e) {
            throw new GeneralException("Error loading repository policies", e);
        }
    }

    /**
     * Does nothing at init time.
     */
    @Override
    public void init(PolicyFinder finder) {
    }

    /**
     * Always returns true, indicating that this impl supports finding policies
     * based on a request.
     */
    @Override
    public boolean isRequestSupported() {
        return true;
    }

    /**
     * Gets a deny-biased policy set that includes all repository-wide and
     * object-specific policies.
     */
    @Override
    public PolicyFinderResult findPolicy(EvaluationCtx context) {
        PolicyFinderResult policyFinderResult = null;
        try {
            List<AbstractPolicy> policies = new ArrayList<AbstractPolicy>(m_repositoryPolicies);
            String pid = getPid(context);
            if (pid != null && !"".equals(pid)) {
                AbstractPolicy objectPolicyFromObject = m_policyStrategy.loadObjectPolicy(m_policyParser.copy(), pid, m_validateObjectPoliciesFromDatastream);
                if (objectPolicyFromObject != null) {
                    policies.add(objectPolicyFromObject);
                }
            }
            PolicyCombiningAlgorithm policyCombiningAlgorithm =
                    (PolicyCombiningAlgorithm) Class
                            .forName(m_combiningAlgorithm).newInstance();
            PolicySet policySet =
                    new PolicySet(new URI(""),
                                  policyCombiningAlgorithm,
                                  null /*
                                   * no general target beyond those of
                                   * multiplexed individual policies
                                   */,
                                  policies);
            policyFinderResult = new PolicyFinderResult(policySet);
        } catch (Exception e) {
            logger.warn("PolicyFinderModule seriously failed to evaluate a policy ", e);
            policyFinderResult =
                    new PolicyFinderResult(new Status(ERROR_CODE_LIST, e
                            .getMessage()));
        }
        return policyFinderResult;
    }

    // get the pid from the context, or null if unable
    public static String getPid(EvaluationCtx context) {
        URI resourceIdType = null;
        URI resourceIdId = null;
        try {
            resourceIdType = new URI(StringAttribute.identifier);
            resourceIdId = new URI(Constants.OBJECT.PID.uri);
        } catch (URISyntaxException e) {
            throw new FaultException("Bad URI syntax", e);
        }
        EvaluationResult attribute
                = context.getResourceAttribute(resourceIdType,
                                               resourceIdId,
                                               null);
        Object element = getAttributeFromEvaluationResult(attribute);
        if (element == null) {
            logger.debug("PolicyFinderModule:getPid exit on "
                    + "can't get contextId on request callback");
            return null;
        }

        if (!(element instanceof StringAttribute)) {
            logger.debug("PolicyFinderModule:getPid exit on "
                    + "couldn't get contextId from xacml request "
                    + "non-string returned");
            return null;
        }

        return ((StringAttribute) element).getValue();
    }

    // copy of code in AttributeFinderModule; consider refactoring
    private static final Object getAttributeFromEvaluationResult(EvaluationResult attribute) {
        if (attribute.indeterminate()) {
            return null;
        }

        if (attribute.getStatus() != null
                && !Status.STATUS_OK.equals(attribute.getStatus())) {
            return null;
        }

        AttributeValue attributeValue = attribute.getAttributeValue();
        if (!(attributeValue instanceof BagAttribute)) {
            return null;
        }

        BagAttribute bag = (BagAttribute) attributeValue;
        if (1 != bag.size()) {
            return null;
        } else {
            return bag.iterator().next();
        }
    }

    // load and parse all policies (*.xml) from a given directory, recursively
    private static List<AbstractPolicy> loadPolicies(PolicyParser parser,
                                                     boolean validate,
                                                     File dir)
            throws IOException, ValidationException {
        List<AbstractPolicy> policies = new ArrayList<AbstractPolicy>();
        for (File file: dir.listFiles()) {
            if (file.isDirectory()) {
                policies.addAll(loadPolicies(parser, validate, file));
            } else {
                if (file.getName().endsWith(".xml")) {
                    logger.info("Loading policy: " + file.getPath());
                    InputStream policyStream = new FileInputStream(file);
                    policies.add(parser.parse(policyStream, validate));
                }
            }
        }
        return policies;
    }

}
