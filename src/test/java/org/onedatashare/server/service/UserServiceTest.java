package org.onedatashare.server.service;

import org.junit.Test;
import org.onedatashare.server.model.core.User;
import org.springframework.beans.factory.annotation.Autowired;

//@RunWith(SpringRunner.class)
//@ContextConfiguration(classes = UserService.class)
//@ActiveProfiles("test")
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @Test
    public void createUserTest() throws Exception {
//    User user = new User("vanditsa@buffalo.edu", "asdasd");
//    userService.createUser(user).subscribe(System.out::println);
    }

    @Test
    public void createUser_givenNothing_createsUser() {

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
