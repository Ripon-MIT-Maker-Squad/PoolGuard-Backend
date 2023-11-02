package com.riponmakers.lifeguard;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.riponmakers.lifeguard.Debugging.Logger;
import com.riponmakers.lifeguard.UserDatabase.DatabaseConnector;
import com.riponmakers.lifeguard.UserDatabase.DeviceService;
import com.riponmakers.lifeguard.UserDatabase.NeighborService;
import com.riponmakers.lifeguard.UserDatabase.UserService;
import com.riponmakers.lifeguard.endpoints.DeviceEndpoint;
import com.riponmakers.lifeguard.endpoints.NeighborEndpoint;
import com.riponmakers.lifeguard.endpoints.UserEndpoint;
import io.helidon.config.Config;
import io.helidon.openapi.OpenAPISupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;


public class LifeGuardWebServer {
    private static final ObjectMapper mapper = new ObjectMapper();
    public static void main(String[] args) {
        /*
         * Database Connector talks to psql database on the webserver, while
         * the UserService contains rest API methods that are called
         * when a certain path/parameters are used
         *
         * Two separate servers, and different database connections need to be made for
         * testing and for production
         */

        final Logger logger = new Logger();

        final DatabaseConnector databaseConnector = new DatabaseConnector(
                "jdbc:postgresql://localhost:5432/lifeguarddb",
                "lifeguard",
                "fc84*th4"
        );
        final DeviceService deviceService = new DeviceService(databaseConnector, "lifeguarddb", "devices", "lifeguardusers");
        final NeighborService neighborService = new NeighborService(databaseConnector, "lifeguarddb", "neighbors");
        final UserService userService = new UserService(databaseConnector, "lifeguarddb", "lifeguardusers");

        final DatabaseConnector testDatabaseConnector = new DatabaseConnector(
                "jdbc:postgresql://localhost:5432/testlifeguarddb",
                "testlifeguard",
                "y24iphio"
        );
        final UserService testUserService = new UserService(testDatabaseConnector, "testlifeguarddb", "testlifeguardusers");
        final NeighborService testNeighborService = new NeighborService(databaseConnector, "testlifeguarddb", "testneighbors");
        final DeviceService testDeviceService = new DeviceService(testDatabaseConnector, "testlifeguarddb", "testDevices", "testlifeguardusers");
        logger.logLine("databases connected");

        /*
         * Extract the exposed parameters in the url to
         * validate and perform operations,
         * Comments provided directly from the LifeGuard API docs
         */
        var serverRouting = routing(userService, deviceService, neighborService, logger);
        assert serverRouting != null;
        logger.logLine("production server routing created");

        WebServer server = WebServer.builder(serverRouting).port(1026).build();
        server.start();
        logger.logLine("production server started");


        var testServerRouting = routing(testUserService, testDeviceService, testNeighborService, logger);
        assert testServerRouting != null;
        logger.logLine("test server routing created");

        WebServer testServer = WebServer.builder(testServerRouting).port(1027).build();
        testServer.start();
        logger.logLine("test server started");

        /*
         * Swagger API documentation server
         * */

        //Automatically loads /resources/META-INF/openapi.yaml
        Config config = Config.create();

        //create /openapi pathing
        Routing openAPIRouting = Routing.builder()
                .register(OpenAPISupport.create(config))
                .build();
        logger.logLine("api routing created");

        WebServer openAPIServer = WebServer.builder(openAPIRouting).port(1028).build();
        openAPIServer.start();
        logger.logLine("api server started");

    }

    private static Routing routing(UserService userService, DeviceService deviceService, NeighborService neighborService, Logger logger) {
        var userEndpoint = new UserEndpoint(userService, deviceService, neighborService, mapper, logger);
        var deviceEndpoint = new DeviceEndpoint(deviceService, userService, mapper, logger);
        var neighborEndpoint = new NeighborEndpoint(neighborService, userService, mapper, logger);
        Routing routing = Routing.builder()
                // This post does not need a device id because that'll happen after
                // the account is created
                .get("/user", userEndpoint::get)
                .post("/user", userEndpoint::post)
                .delete("/user", userEndpoint::delete)
                .get("/device", deviceEndpoint::get)
                .post("/device", deviceEndpoint::post)
                .delete("/device", deviceEndpoint::delete)
                .get("/neighbor", neighborEndpoint::get)
                .get("/neighbor", neighborEndpoint::post)
                .get("/neighbor", neighborEndpoint::delete)
                .build();
        return routing;
    }
}
