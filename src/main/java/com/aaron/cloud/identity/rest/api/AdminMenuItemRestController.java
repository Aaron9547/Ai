package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.security.entity.SysAdminMenuItem;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.admin.MenuCatalogApplicationService;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminMenuItemRestController extends ApiV1ControllerBases.AdminMenuItems {

    private final MenuCatalogApplicationService menuCatalogApplicationService;

    @GetMapping
    public List<SysAdminMenuItem> list() {
        return menuCatalogApplicationService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SysAdminMenuItem create(@RequestBody CreateMenuItemBody body) {
        return menuCatalogApplicationService.create(
                body.getMenuCode(), body.getTitleZh(), body.getRoutePath(), body.getSortOrder() == null ? 0 : body.getSortOrder());
    }

    @PutMapping("/{id}")
    public SysAdminMenuItem update(@PathVariable long id, @RequestBody UpdateMenuItemBody body) {
        return menuCatalogApplicationService.update(
                id, body.getTitleZh(), body.getRoutePath(), body.getSortOrder(), body.getEnabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        menuCatalogApplicationService.delete(id);
    }

    @Data
    public static class CreateMenuItemBody {
        private String menuCode;
        private String titleZh;
        private String routePath;
        private Integer sortOrder;
    }

    @Data
    public static class UpdateMenuItemBody {
        private String titleZh;
        private String routePath;
        private Integer sortOrder;
        private ToggleState enabled;
    }
}
