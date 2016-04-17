package org.openremote.test.controller2;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.openremote.component.controller2.Controller2Component;
import org.openremote.component.device.DeviceComponent;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.message.MessageBrokerContext;
import org.openremote.container.message.MessageBrokerService;
import org.openremote.manager.shared.device.Device;

import java.util.ArrayList;
import java.util.List;

// TODO This is an example service for testing, replaced later with production code
public class Controller2Service implements ContainerService {

    public List<Device> addedDevices = new ArrayList<>();
    public List<Device> removedDevices = new ArrayList<>();
    public List<Device> updatedDevices = new ArrayList<>();
    public List<Device> discoveredItems = new ArrayList<>();
    public List<String> receivedSensorData = new ArrayList<>();
    public ProducerTemplate messageProducerTemplate;

    @Override
    public void init(Container container) throws Exception {
    }

    @Override
    public void configure(Container container) throws Exception {
        MessageBrokerService messageBrokerService = container.getService(MessageBrokerService.class);
        MessageBrokerContext context = messageBrokerService.getContext();

        configure(context);

        messageProducerTemplate = context.createProducerTemplate();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("mockController2:http://10.0.0.123:8080/inventory")
                        .routeId("Device Inventory")
                        .choice()
                            .when(header(DeviceComponent.HEADER_DEVICE_ACTION).isEqualTo(DeviceComponent.Action.ADD))
                                .process(exchange -> {
                                    Device device = exchange.getIn().getBody(Device.class);
                                    addedDevices.add(device);
                            }).endChoice()
                            .when(header(DeviceComponent.HEADER_DEVICE_ACTION).isEqualTo(DeviceComponent.Action.REMOVE))
                            .process(exchange -> {
                                Device device = exchange.getIn().getBody(Device.class);
                                removedDevices.add(device);
                            }).endChoice()
                            .when(header(DeviceComponent.HEADER_DEVICE_ACTION).isEqualTo(DeviceComponent.Action.UPDATE))
                            .process(exchange -> {
                                Device device = exchange.getIn().getBody(Device.class);
                                updatedDevices.add(device);
                            }).end();

                from("direct:triggerDiscovery")
                    .routeId("Trigger discovery")
                    .to("mockController2:http://10.0.0.123:8080/discovery");

                from("mockController2:http://10.0.0.123:8080/discovery")
                    .routeId("Discovered devices")
                    .process(exchange -> {
                        List<Device> discovered = exchange.getIn().getBody(List.class);
                        discoveredItems.addAll(discovered);
                    });

                from("mockController2:http://10.0.0.123:8080")
                    .routeId("Received sensor values")
                    .process(exchange -> {
                        receivedSensorData.add(exchange.getIn().getBody(String.class));
                    });

                from("direct:write")
                    .routeId("Write Resource")
                    .to("mockController2:http://10.0.0.123:8080");

                from("direct:read")
                        .routeId("Read Resource")
                        .to("mockController2:http://10.0.0.123:8080");
            }
        });
    }

    protected void configure(MessageBrokerContext context) {
        context.addComponent("mockController2", new Controller2Component());
    }

    @Override
    public void start(Container container) {
    }

    @Override
    public void stop(Container container) {
    }

}