package se.iths.gateway_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StatusPageTests {
    @Autowired
    private MockMvc mockMvc;

    // Rotadressen och direktadressen ska fungera utan inloggning och visa rätt tjänst.
    @Test
    void statusPageIsPublic() throws Exception {
        for (String address : new String[] { "/", "/index.html" }) {
            mockMvc.perform(get(address))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string(containsString("Gateway Service")))
                    .andExpect(content().string(containsString("Service is running")));
        }
    }
}
