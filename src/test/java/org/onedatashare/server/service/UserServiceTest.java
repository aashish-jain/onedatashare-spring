package org.onedatashare.server.service;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.onedatashare.server.model.core.User;
import org.springframework.beans.factory.annotation.Autowired;

//@RunWith(SpringRunner.class)
//@ContextConfiguration(classes = UserService.class)
//@ActiveProfiles("test")
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void createUserTest() throws Exception {
//    User user = new User("vanditsa@buffalo.edu", "asdasd");
//    userService.createUser(user).subscribe(System.out::println);
    }

    @Test
    public void createUser_givenBlankEmailAndPassword_throwsRuntimeExceptionAndDisplaysCorrectMessage() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No password was provided");
        User user = new User("","");
        userService.createUser(user).subscribe();
    }

    @Test
    public void createUser_givenRandomEmailAndBlankPassword_throwsRuntimeExceptionAndDisplaysCorrectMessage() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No password was provided");
        User user = new User("ryandils@buffalo.edu","");
        userService.createUser(user).subscribe();
    }

    @Test
    public void createUser_givenBlankEmailAndRandomPassword_throwsNullPointerException() {
        thrown.expect(NullPointerException.class);
        User user = new User("","password");
        userService.createUser(user).subscribe();
    }

    @Test
    public void getGlobusClient_givenGlobusClient_returnsGlobsuClient() {

    }

    @Test
    public void removeIfExpired_givenExpiredObject_successfullyRemoves() {

    }

    @Test
    public void verifyEmail_givenValidEmail_successfullyVerifiesEmail() {

    }

    @Test
    public void verifyEmail_givenInvalidEmail_unsuccessfullyVerifiesEmail() {

    }

    @Test
    public void isAdmin_givenAdmin_returnsTrue() {

    }

    @Test
    public void isAdmin_givenSomeoneThatIsNotAdmin_returnsFalse() {

    }

    @Test
    public void getGlobusClient_givenNoCredentials_returnsNull() {

    }
}
