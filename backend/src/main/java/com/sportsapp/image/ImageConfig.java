package com.sportsapp.image;

import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves stored images. A new upload always gets a new file name, so a URL's content never changes. */
@Configuration
public class ImageConfig implements WebMvcConfigurer {

  private final ImageStore store;

  public ImageConfig(ImageStore store) {
    this.store = store;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/api/images/**")
        .addResourceLocations(store.dir().toUri().toString())
        .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
  }
}
