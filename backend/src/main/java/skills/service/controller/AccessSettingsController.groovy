package skills.service.controller

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.*
import skills.service.auth.AuthMode
import skills.service.auth.UserInfoService
import skills.service.controller.exceptions.SkillException
import skills.service.controller.exceptions.SkillsValidator
import skills.service.controller.result.model.RequestResult
import skills.service.datastore.services.AccessSettingsStorageService
import skills.service.profile.EnableCallStackProf
import skills.storage.model.auth.AllowedOrigin
import skills.storage.model.auth.RoleName
import skills.storage.model.auth.UserRole

@RestController
@RequestMapping("/admin")
@Slf4j
@EnableCallStackProf
class AccessSettingsController {

    @Autowired
    UserInfoService userInfoService

    @Autowired
    UserDetailsService userDetailsService

    @Autowired
    AccessSettingsStorageService accessSettingsStorageService

    @Value('#{securityConfig.authMode}}')
    AuthMode authMode = AuthMode.DEFAULT_AUTH_MODE

    @RequestMapping(value = "/projects/{projectId}/userRoles", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    List<UserRole> getProjectUserRoles(@PathVariable("projectId") String projectId) {
        return accessSettingsStorageService.getUserRoles(projectId)
    }

    @RequestMapping(value = "/projects/{projectId}/users/{userId}/roles", method = RequestMethod.GET)
    List<UserRole>  getUserRoles(
            @PathVariable("projectId") String projectId,
            @PathVariable("userId") String userId) {
        accessSettingsStorageService.getUserRoles(projectId, userId)
    }

    @RequestMapping(value = "/projects/{projectId}/users/{userId}/roles/{roleName}", method = RequestMethod.DELETE)
    void deleteUserRole(
            @PathVariable("projectId") String projectId,
            @PathVariable("userId") String userId, @PathVariable("roleName") RoleName roleName) {
        accessSettingsStorageService.deleteUserRole(userId, projectId, roleName)
    }

    @RequestMapping(value = "/projects/{projectId}/users/{userKey}/roles/{roleName}", method = [RequestMethod.PUT, RequestMethod.POST])
    RequestResult addUserRole(
            @PathVariable("projectId") String projectId,
            @PathVariable("userKey") String userKey, @PathVariable("roleName") RoleName roleName) {
        accessSettingsStorageService.addUserRole(getUserId(userKey), projectId, roleName)
        return new RequestResult(success: true)
    }

    @RequestMapping(value = "/projects/{projectId}/allowedOrigins", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    List<AllowedOrigin> getAllowedOrigins(@PathVariable("projectId") String projectId) {
        return accessSettingsStorageService.getAllowedOrigins(projectId)
    }

    @RequestMapping(value = "/projects/{projectId}/allowedOrigins", method = [RequestMethod.PUT, RequestMethod.POST])
    AllowedOrigin saveOrUpdateAllowedOrigin(@PathVariable("projectId") String projectId, @RequestBody AllowedOrigin update) {
        SkillsValidator.isNotBlank(projectId, "Project Id")
        SkillsValidator.isNotBlank(update.allowedOrigin, "Allowed Origin", projectId)
        SkillsValidator.isFirstOrMustEqualToSecond(update.projectId, projectId, "Project Id")

        return accessSettingsStorageService.saveOrUpdateAllowedOrigin(projectId, update)
    }

    @RequestMapping(value = "/projects/{projectId}/allowedOrigins/{allowedOriginId}", method = RequestMethod.DELETE)
    void deleteAllowedOrigin(
            @PathVariable("projectId") String projectId, @PathVariable("allowedOriginId") Integer allowedOriginId) {
        accessSettingsStorageService.deleteAllowedOrigin(projectId, allowedOriginId)
    }

    private String getUserId(String userKey) {
        // userKey will be the userId when in FORM authMode, or the DN when in PKI auth mode.
        // When in PKI auth mode, the userDetailsService implementation will create the user
        // account if the user is not already a portal user (PkiUserDetailsService).
        // In the case of FORM authMode, the userKey is the userId and the user is expected
        // to already have a portal user account in the database
        if (authMode == AuthMode.PKI) {
            try {
                return userDetailsService.loadUserByUsername(userKey).username
            } catch (UsernameNotFoundException e) {
                throw new SkillException("User [$userKey] does not exist")
            }
        } else {
            return userKey
        }
    }

}
