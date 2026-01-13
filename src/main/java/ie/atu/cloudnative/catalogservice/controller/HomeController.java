package ie.atu.cloudnative.catalogservice.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String getGreeting() {
        return "Welcome to the Cloud Native Book Catalog!";
    }

    @GetMapping("/greeting/{name}")
    public String getPersonalGreeting(@PathVariable String name) {
        return "Hello " + name + ", welcome to the book catalog!,, I am yashwanth ";
    }

    @GetMapping("/health")
    public String health() throws UnknownHostException {
        String hostname = InetAddress.getLocalHost().getHostName();
        return "Application is healthy and running on: " + hostname +
               " (" + System.getProperty("os.name") + ")";
    }
}