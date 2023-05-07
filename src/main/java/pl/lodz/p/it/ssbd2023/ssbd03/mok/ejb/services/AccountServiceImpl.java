package pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.services;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionSynchronization;
import jakarta.ejb.Stateful;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.NoResultException;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.credential.Password;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.ws.rs.ForbiddenException;
import pl.lodz.p.it.ssbd2023.ssbd03.auth.ConfirmationTokenGenerator;
import pl.lodz.p.it.ssbd2023.ssbd03.auth.JwtGenerator;
import pl.lodz.p.it.ssbd2023.ssbd03.common.AbstractService;
import pl.lodz.p.it.ssbd2023.ssbd03.config.Roles;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.*;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.Account;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.AccountConfirmationToken;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.Owner;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.PersonalData;
import pl.lodz.p.it.ssbd2023.ssbd03.exceptions.AppException;
import pl.lodz.p.it.ssbd2023.ssbd03.exceptions.account.AccountPasswordException;
import pl.lodz.p.it.ssbd2023.ssbd03.interceptors.TrackerInterceptor;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade.*;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.mail.MailSender;
import pl.lodz.p.it.ssbd2023.ssbd03.util.BcryptHashGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Stateful
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
@Interceptors({TrackerInterceptor.class})
public class AccountServiceImpl extends AbstractService implements AccountService, SessionSynchronization {
    @Inject
    private PersonalDataFacade personalDataFacade;

    @Inject
    private OwnerFacade ownerFacade;

    @Inject
    private AccountFacade accountFacade;

    @Inject
    private ManagerFacade managerFacade;

    @Inject
    private AdminFacade adminFacade;

    @Inject
    private AccountConfirmationTokenFacade accountConfirmationTokenFacade;

    @Inject
    private MailSender mailSender;

    @Inject
    private ConfirmationTokenGenerator confirmationTokenGenerator;

    @Inject
    private JwtGenerator jwtGenerator;

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private BcryptHashGenerator bcryptHashGenerator;

    @Override
    public void createOwner(Account account) {
        account.setPassword(bcryptHashGenerator.generate(account.getPassword().toCharArray()));
        account.setRegisterDate(LocalDateTime.now());

        accountFacade.create(account);

        AccountConfirmationToken accountConfirmationToken = new AccountConfirmationToken(
                confirmationTokenGenerator.createAccountConfirmationToken(), account);
        accountConfirmationTokenFacade.create(accountConfirmationToken);

        mailSender.sendLinkToActivateAccountToEmail(account.getEmail(), "Activate account", accountConfirmationToken.getTokenValue());
    }

    @Override
    public void confirmAccountFromActivationLink(String confirmationToken) {
        AccountConfirmationToken accountConfirmationToken = accountConfirmationTokenFacade.getActivationTokenByTokenValue(confirmationToken);

        Account accountToActivate = accountConfirmationToken.getAccount();
        accountToActivate.setIsActive(true);
        accountFacade.edit(accountToActivate);

        accountConfirmationTokenFacade.remove(accountConfirmationToken);
    }

    @Override
    @RolesAllowed(Roles.GUEST)
    public String authenticate(String username, String password) {
        final UsernamePasswordCredential usernamePasswordCredential = new UsernamePasswordCredential(username, new Password(password));
        final CredentialValidationResult credentialValidationResult = identityStoreHandler.validate(usernamePasswordCredential);
        if (credentialValidationResult.getStatus().equals(CredentialValidationResult.Status.VALID)) {
            final Set<String> roles = credentialValidationResult.getCallerGroups();
            return jwtGenerator.generateJWT(username, roles);
        }
        throw AppException.invalidCredentialsException();
    }

    @Override
    @RolesAllowed(Roles.OWNER)
    public void changePhoneNumber(String newPhoneNumber) {
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        Owner owner = account.getAccessLevels().stream()
                .filter(accessLevel -> accessLevel instanceof Owner)
                .map(accessLevel -> (Owner) accessLevel)
                .findAny()
                .orElseThrow(AppException::createAccountIsNotOwnerException);
        if (newPhoneNumber.equals(owner.getPhoneNumber())) {
            throw AppException.createCurrentPhoneNumberException();
        } else {
            if (!ownerFacade.checkIfAnOwnerExistsByPhoneNumber(newPhoneNumber)) {
                owner.setPhoneNumber(newPhoneNumber);
                ownerFacade.edit(owner);
            }
        }
    }

    @Override
    @RolesAllowed({Roles.ADMIN, Roles.MANAGER, Roles.OWNER})
    public void changePassword(String oldPassword, String newPassword, String newRepeatedPassword) throws AccountPasswordException {
        if (oldPassword.equals(newPassword)) {
            throw new AccountPasswordException("Old password and new password are the same.");
        }
        if (!newPassword.equals(newRepeatedPassword)) {
            throw new AccountPasswordException("New password and new repeated password are not the same.");
        }
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        if (!bcryptHashGenerator.generate(oldPassword.toCharArray()).equals(account.getPassword())) {
            throw new AccountPasswordException("Old password is incorrect");
        }
        final String newPasswordHash = bcryptHashGenerator.generate(newPassword.toCharArray());
        account.setPassword(newPasswordHash);
        accountFacade.edit(account);
    }

    @Override
    public void editSelfPersonalData(String firstName, String surname) throws NoResultException {
        try {
            final String username = securityContext.getCallerPrincipal().getName();
            editPersonalData(username, firstName, surname);
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    @Override
    public void editUserPersonalData(String username, String firstName, String surname) throws ForbiddenException {
        final String editor = securityContext.getCallerPrincipal().getName();
        final Account editorAccount = accountFacade.findByUsername(editor);
        final Account editableAccount = accountFacade.findByUsername(username);

        if (editorAccount.getAccessLevels().stream().anyMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
            editPersonalData(username, firstName, surname);
        } else if (editableAccount.getAccessLevels().stream().noneMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
            editPersonalData(username, firstName, surname);
        } else {
            throw new ForbiddenException("Cannot edit other user personal data due to not supported role");
        }
    }

    private void editPersonalData(String username, String firstName, String surname) {
        try {
            PersonalData personalData = personalDataFacade.findByLogin(username);

            personalData.setFirstName(firstName);
            personalData.setSurname(surname);

            personalDataFacade.edit(personalData);
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    @Override
    public void disableUserAccount(String username) throws NoResultException {
        editUserEnableFlag(username, false);
    }

    @Override
    public void enableUserAccount(String username) throws NoResultException {
        editUserEnableFlag(username, true);
    }

    private void editUserEnableFlag(String username, boolean flag) throws NoResultException {
        try {
            final String editor = securityContext.getCallerPrincipal().getName();
            final Account editorAccount = accountFacade.findByUsername(editor);
            final Account editableAccount = accountFacade.findByUsername(username);

            if (editorAccount.equals(editableAccount)) {
                throw new ForbiddenException("Cannot edit yours enable flag.");
            }

            if (editorAccount.getAccessLevels().stream().anyMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
                setUserEnableFlag(username, flag);
            } else if (editableAccount.getAccessLevels().stream().noneMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
                setUserEnableFlag(username, flag);
            } else {
                throw new ForbiddenException("Cannot edit other user enable flag due to not supported role.");
            }
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    private void setUserEnableFlag(String username, boolean flag) throws NoResultException {
        try {
            final Account editableAccount = accountFacade.findByUsername(username);
            editableAccount.setIsEnable(flag);
            accountFacade.edit(editableAccount);
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    @Override
    public void addAccessLevelManager(String username, String license) throws NoResultException{
        Account account = accountFacade.findByUsername(username);
        if(account == null) {
            throw AppException.createAccountNotExistsException(null);
        } else {
            if(account.getIsActive()) {
                if(managerFacade.findByLicense(license) != null) {
                    throw AppException.createAccountWithLicenseExistsException();
                } else {
                    if (!account.getAccessLevels().stream()
                            .anyMatch(accessLevel -> accessLevel.getAccessLevel().equals(Roles.MANAGER)))  {
                        Manager manager = new Manager(license);
                        manager.setAccount(account);
                        account.getAccessLevels().add(manager);
                    } else {
                        throw AppException.theAccessLevelisAlreadyGranted();
                    }
                }
            } else {
                throw AppException.accountIsNotActivated();
            }
        }
    }

    @Override
    public void addAccessLevelOwner(String username, String phoneNumber) throws NoResultException{
            Account account = accountFacade.findByUsername(username);
            if(account == null) {
                throw AppException.createAccountNotExistsException(null);
            } else {
                if (account.getIsActive()) {
                    if (ownerFacade.findByPhoneNumber(phoneNumber) != null) {
                        throw AppException.createAccountWithNumberExistsException();
                    } else {
                        if (!account.getAccessLevels().stream()
                                .anyMatch(accessLevel -> accessLevel.getAccessLevel().equals(Roles.OWNER))) {
                            Owner owner = new Owner(phoneNumber);
                            owner.setAccount(account);
                            account.getAccessLevels().add(owner);
                        } else {
                            throw AppException.theAccessLevelisAlreadyGranted();
                        }
                    }
                } else {
                    throw AppException.accountIsNotActivated();
                }
            }
    }

    @Override
    public void addAccessLevelAdmin(String username) throws NoResultException{
            Account account = accountFacade.findByUsername(username);
            if(account == null) {
                throw AppException.createAccountNotExistsException(null);
            } else {
                if(account.getIsActive()) {
                    if(!account.getAccessLevels().stream()
                            .anyMatch(accessLevel -> accessLevel.getAccessLevel().equals(Roles.ADMIN))) {
                        Admin admin = new Admin();
                        admin.setAccount(account);
                        account.getAccessLevels().add(admin);
                    } else {
                        throw AppException.theAccessLevelisAlreadyGranted();
                    }
                } else {
                    throw AppException.accountIsNotActivated();
                }
            }
    }

    @Override
    public void revokeAccessLevel(String username, String access) throws NoResultException{
        final String adminUsername = securityContext.getCallerPrincipal().getName();
        if(!username.equals(adminUsername)) {
            Account account = accountFacade.findByUsername(username);
                if(account == null) {
                    throw AppException.createAccountNotExistsException(null);
                } else {
                    if(account.getIsActive()) {
                        final int size = account.getAccessLevels().size();
                            if(access.equals(Roles.MANAGER)) {
                                Manager manager = account.getAccessLevels().stream()
                                        .filter(accessLevel -> accessLevel instanceof Manager)
                                        .map(accessLevel -> (Manager) accessLevel)
                                        .findAny()
                                        .orElseThrow(AppException::createAccountIsNotManagerException);
                                if(size > 1) {
                                    final int index1 = IntStream.range(0, account.getAccessLevels().size())
                                            .filter(i -> account.getAccessLevels().get(i) == manager)
                                            .findFirst()
                                            .orElse(-1);
                                    account.getAccessLevels().remove(index1);
                                    managerFacade.remove(manager);
                                } else {
                                    throw AppException.revokeTheOnlyLevelOfAccess();
                                }
                            }
                            if(access.equals(Roles.ADMIN)) {
                                Admin admin = account.getAccessLevels().stream()
                                        .filter(accessLevel -> accessLevel instanceof Admin)
                                        .map(accessLevel -> (Admin) accessLevel)
                                        .findAny()
                                        .orElseThrow(AppException::createAccountIsNotAdminException);
                                if(size > 1) {
                                    final int index = IntStream.range(0, account.getAccessLevels().size())
                                            .filter(i -> account.getAccessLevels().get(i) == admin)
                                            .findFirst()
                                            .orElse(-1);
                                    account.getAccessLevels().remove(index);
                                    adminFacade.remove(admin);
                                } else {
                                    throw AppException.revokeTheOnlyLevelOfAccess();
                                }
                            }
                            if(access.equals(Roles.OWNER)) {
                                Owner owner = account.getAccessLevels().stream()
                                        .filter(accessLevel -> accessLevel instanceof Owner)
                                        .map(accessLevel -> (Owner) accessLevel)
                                        .findAny()
                                        .orElseThrow(AppException::createAccountIsNotOwnerException);
                                if(size > 1) {
                                    final int index = IntStream.range(0, account.getAccessLevels().size())
                                            .filter(i -> account.getAccessLevels().get(i) == owner)
                                            .findFirst()
                                            .orElse(-1);
                                    account.getAccessLevels().remove(index);
                                    ownerFacade.remove(owner);
                                } else {
                                    throw AppException.revokeTheOnlyLevelOfAccess();
                                }
                            }
                    } else {
                        throw AppException.accountIsNotActivated();
                    }
            }
        } else {
            throw AppException.addingAnAccessLevelToTheSameAdminAccount();
        }
    }

    @Override
    public List<Account> getListOfAccounts(String sortBy, int pageNumber) {
        return accountFacade.getListOfAccountsWithFilterParams(sortBy, pageNumber);
    }
}
