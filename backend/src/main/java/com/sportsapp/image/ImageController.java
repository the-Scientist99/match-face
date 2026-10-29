package com.sportsapp.image;

import com.sportsapp.common.Auth;
import com.sportsapp.image.ImageService.Owner;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code PUT} uploads (multipart part {@code file}, JPEG or PNG up to 5 MB), {@code DELETE} removes. */
@RestController
@RequestMapping("/api")
public class ImageController {

  public record ImageResponse(String imageUrl) {}

  private final ImageService images;

  public ImageController(ImageService images) {
    this.images = images;
  }

  @PutMapping(path = "/players/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ImageResponse setPlayer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam MultipartFile file) {
    return set(Owner.PLAYER, jwt, id, file);
  }

  @DeleteMapping("/players/{id}/image")
  ImageResponse removePlayer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return set(Owner.PLAYER, jwt, id, null);
  }

  @PutMapping(path = "/teams/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ImageResponse setTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam MultipartFile file) {
    return set(Owner.TEAM, jwt, id, file);
  }

  @DeleteMapping("/teams/{id}/image")
  ImageResponse removeTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return set(Owner.TEAM, jwt, id, null);
  }

  @PutMapping(path = "/competitions/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ImageResponse setCompetition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam MultipartFile file) {
    return set(Owner.COMPETITION, jwt, id, file);
  }

  @DeleteMapping("/competitions/{id}/image")
  ImageResponse removeCompetition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return set(Owner.COMPETITION, jwt, id, null);
  }

  private ImageResponse set(Owner owner, Jwt jwt, UUID id, MultipartFile file) {
    return new ImageResponse(ImageStore.url(images.replace(owner, Auth.userId(jwt), id, file)));
  }
}
