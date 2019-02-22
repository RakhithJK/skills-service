package skills.service.auth.form

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.security.web.context.HttpRequestResponseHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import skills.service.auth.SkillsAuthorizationException
import skills.service.auth.UserAuthService
import skills.service.auth.UserInfo

import javax.persistence.EntityManager
import javax.persistence.PersistenceContext
import javax.transaction.Transactional

import static skills.service.auth.form.AuthorizationServerConfig.SKILLS_PROXY_USER

/**
 * this class is responsible for converting OAuth2 authenticated principal's to UserInfo objects
 * and storing them in the SecurityContextHolder.  It also reload the users granted_authorities
 * on each request.
 */
@Slf4j
class SkillsHttpSessionSecurityContextRepository extends HttpSessionSecurityContextRepository {

    @Autowired
    OAuth2UserConverterService userConverter

    @Autowired
    UserAuthService userAuthService

    @PersistenceContext
    protected EntityManager em

    @Override
    @Transactional
    SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {

        // Let the parent class actually get the SecurityContext from the HTTPSession first.
        SecurityContext context = super.loadContext(requestResponseHolder)

        boolean loadedFromSession = true
        Authentication auth = context.getAuthentication()
        if (!auth || isProxyiedAuth(auth)) {
            // if the Authentication is not available from the HTTP session (initial request for this session),
            // or if it is an OAuth2 proxied principal we want to reload on each request so see if the Authentication
            // object was made  available in the SecurityContextHolder by an Authentication filter.
            auth = SecurityContextHolder.getContext().getAuthentication()
            loadedFromSession = false
        }
        if (auth) {
            if (auth instanceof OAuth2Authentication) {
                // OAuth2Authentication is used when then the OAuth2 client uses the client_credentials grant_type we
                // look for a custom "proxy_user" field where the trusted client must specify the skills user that the
                // request is being performed on behalf of.  The proxy_user field is required for client_credentials grant_type
                OAuth2AuthenticationDetails oauthDetails = (OAuth2AuthenticationDetails) auth.getDetails()
                Map claims = oauthDetails.getDecodedDetails()
                if (claims && claims.containsKey(SKILLS_PROXY_USER)) {
                    String proxyUserId = claims.get(SKILLS_PROXY_USER)
                    if (!proxyUserId) {
                        throw new SkillsAuthorizationException("client_credentials grant_type must specify $SKILLS_PROXY_USER field for ")
                    }
                    log.info("Loading proxyUser [${proxyUserId}]")
                    UserInfo currentUser = new UserInfo(
                            username: proxyUserId,
                            proxied: true,
                            proxyingSystemId: auth.principal
                    )
                    // Create new Authentication using UserInfo
                    auth = new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.authorities)
                }
            } else if (auth && auth instanceof OAuth2AuthenticationToken && auth.principal instanceof OAuth2User) {
                String clientId = auth.authorizedClientRegistrationId
                OAuth2User oAuth2User = auth.principal
                // convert to UserInfo using configured converter for registrationId (fail if none available)
                UserInfo currentUser = userConverter.convert(clientId, oAuth2User)

                // also create/update the UserInfo in the database.
                if (!em.isJoinedToTransaction()) { em.joinTransaction() }
                currentUser = userAuthService.createOrUpdateUser(currentUser)

                // Create new Authentication using UserInfo
                auth = new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.authorities)
            } else if (auth && auth.principal instanceof UserInfo && loadedFromSession) {
                // reload the granted_authorities for this skills user if loaded from the HTTP Session)
                UserInfo userInfo = auth.principal
                userInfo.authorities = userAuthService.loadAuthorities(userInfo.username)
                auth = new UsernamePasswordAuthenticationToken(userInfo, auth.credentials, userInfo.authorities)
            }

            context.setAuthentication(auth)
        }
        return context
    }

    private boolean isProxyiedAuth(Authentication auth) {
        return (auth && auth.principal && auth.principal instanceof UserInfo && auth.principal.isProxied())
    }
}
