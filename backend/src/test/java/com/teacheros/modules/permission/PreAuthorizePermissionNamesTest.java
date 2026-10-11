package com.teacheros.modules.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreAuthorizePermissionNamesTest {

    private static final Pattern PERM_CALL = Pattern.compile("@perm\\.has\\('([^']+)'\\)");

    @Test
    void everyPermReferenceInControllersIsAKnownPermission() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        int references = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents("com.teacheros")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());

            List<AnnotatedElement> elements = new ArrayList<>();
            elements.add(controller);
            elements.addAll(List.of(controller.getDeclaredMethods()));

            for (AnnotatedElement element : elements) {
                PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(element, PreAuthorize.class);
                if (annotation == null) {
                    continue;
                }
                Matcher matcher = PERM_CALL.matcher(annotation.value());
                while (matcher.find()) {
                    references++;
                    String name = matcher.group(1);
                    assertDoesNotThrow(() -> Permission.valueOf(name),
                            "Unknown permission '" + name + "' in " + controller.getSimpleName());
                }
            }
        }
        assertTrue(references > 0, "expected at least one @perm.has reference to be found");
    }
}
