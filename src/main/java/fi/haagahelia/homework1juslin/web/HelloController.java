package fi.haagahelia.homework1juslin.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


// github github.com/jusju

@Controller
@ResponseBody
public class HelloController {

    @RequestMapping("*")
    public String hello() {
        return "Hello Spring";
    }
 
}
