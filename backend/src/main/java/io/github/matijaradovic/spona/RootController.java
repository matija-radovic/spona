package io.github.matijaradovic.spona;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    String root() {
        return "Spona backend is running";
    }
}
