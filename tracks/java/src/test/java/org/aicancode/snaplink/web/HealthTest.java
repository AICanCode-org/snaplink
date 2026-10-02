package org.aicancode.snaplink.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import org.aicancode.snaplink.link.LinkService;
import org.aicancode.snaplink.link.ShortCodeGenerator;
import org.aicancode.snaplink.support.InMemoryLinkCache;
import org.aicancode.snaplink.support.InMemoryLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Works from day one: the starter already serves /health (no business logic needed). */
class HealthTest {

    @Test
    void healthIsUp() throws Exception {
        LinkService service = new LinkService(new InMemoryLinkRepository(), new InMemoryLinkCache(),
                Clock.systemUTC(), new ShortCodeGenerator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(service)).build();
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.database").value("UP"))
                .andExpect(jsonPath("$.checks.cache").value("UP"));
    }
}
