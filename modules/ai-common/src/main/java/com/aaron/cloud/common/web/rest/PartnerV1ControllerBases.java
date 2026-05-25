package com.aaron.cloud.common.web.rest;

import org.springframework.web.bind.annotation.RequestMapping;

public final class PartnerV1ControllerBases {

    private PartnerV1ControllerBases() {}

    @RequestMapping(AbstractPartnerV1Controller.PREFIX + "/chat")
    public static abstract class Chat extends AbstractPartnerV1Controller {}
}
