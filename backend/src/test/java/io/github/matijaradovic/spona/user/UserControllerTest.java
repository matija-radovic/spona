package io.github.matijaradovic.spona.user;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;

class UserControllerTest {

    private final UserService service = mock(UserService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UserController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void answersTrueForAFreeHandle() throws Exception {
        when(service.isUsernameAvailable("marko")).thenReturn(true);

        mvc.perform(get("/users").param("username", "marko"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void answersFalseForAHandleThatCannotBeRegistered() throws Exception {
        when(service.isUsernameAvailable("admin")).thenReturn(false);

        mvc.perform(get("/users").param("username", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void refusesTheCallWithNoHandleToCheck() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isBadRequest());
    }
}
