package w.whateva.ocl2.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Ocl2Controller {

    @RequestMapping("/")
    public String index() {
        return "index.html";
    }
}
