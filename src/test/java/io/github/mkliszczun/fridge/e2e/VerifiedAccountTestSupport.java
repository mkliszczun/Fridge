package io.github.mkliszczun.fridge.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mkliszczun.fridge.account.EmailVerificationMailer;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP registration flow; only the external mail delivery is replaced. */
abstract class VerifiedAccountTestSupport {
    @Autowired private MockMvc authMvc;
    @Autowired private ObjectMapper authJson;
    @MockitoBean protected EmailVerificationMailer verificationMailer;

    protected MvcResult registerVerified(String email, String password) throws Exception {
        var pending = authMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(authJson.writeValueAsString(Map.of("login", email, "password", password))))
                .andExpect(status().isAccepted()).andReturn();
        String token = authJson.readTree(pending.getResponse().getContentAsString()).path("verificationToken").asText();
        authMvc.perform(post("/auth/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content(authJson.writeValueAsString(Map.of("verificationToken", token))))
                .andExpect(status().isOk());
        var code = ArgumentCaptor.forClass(String.class);
        verify(verificationMailer, atLeastOnce()).send(eq(email), code.capture());
        return authMvc.perform(post("/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(authJson.writeValueAsString(Map.of("verificationToken", token, "code", code.getValue()))))
                .andExpect(status().isOk()).andReturn();
    }
}
