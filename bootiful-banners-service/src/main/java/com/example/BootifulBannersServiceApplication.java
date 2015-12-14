package com.example;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ansi.AnsiPropertySource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.Data;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;


/**
 * I have made so many poor life decisions..
 *
 * @author Josh Long
 */
@EnableConfigurationProperties
@EnableSwagger2
@SpringBootApplication
public class BootifulBannersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootifulBannersServiceApplication.class, args);
    }

    @Bean
    public Docket bootifulBannersApi() {
      return new Docket(DocumentationType.SWAGGER_2)
              .apiInfo(apiInfo())
              .select()
              .paths(PathSelectors.regex("/banner"))
              .build();
    }
    
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("Yet Another Bootiful Banners")
                .description("Convert images into color-coded ASCII text that can be used in your spring-boot banner.txt file.")
                .license("Apache License Version 2.0")
                .licenseUrl("https://raw.githubusercontent.com/joshlong/bootiful-banners/master/LICENSE.txt")
                .version("1.0.0")
                .build();
    }
}

//curl -F "image=@/Users/jlong/Desktop/doge.jpg" -H "Content-Type: multipart/form-data" http://bootiful-banners.cfapps.io/banners
@RestController
@Api("banner-generator")
class BannerGeneratorRestController {

    public static final String[] MEDIA_TYPES = {
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_GIF_VALUE};

    @Autowired
    private BannerProperties properties;

    @Autowired
    private Environment env;

    @RequestMapping(
            value = "/banner",
            method = RequestMethod.POST,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    @ApiOperation("Generate a banner from an image")
    ResponseEntity<String> banner(@ApiParam(value = "the image to convert", required = true) @RequestParam("image") MultipartFile multipartFile,
            @ApiParam("maximum width in characters of banner (default is 72)") @RequestParam(required = false) Integer maxWidth,
            @ApiParam("correction to makes sure height is correct to accomodate the fact that fonts are taller than they are wide. (default is 0.5)") @RequestParam(required = false) Double aspectRatio,
            @ApiParam("whether to invert image for a dark background. (default is false)") @RequestParam(required = false) Boolean invert,
            @ApiParam("wheter to ouput the encoded ansi string instead of the text banner (default is false)") @RequestParam(defaultValue = "false") boolean ansiOutput) throws Exception {
        File image = null;
        
        try {
            image = this.imageFileFrom(multipartFile);
            ImageBanner imageBanner = new ImageBanner(image);

            if(maxWidth == null) {
                maxWidth = this.properties.getMaxWidth();
            }
            if(aspectRatio == null) {
                aspectRatio = this.properties.getAspectRatio();
            }
            if(invert == null) {
                invert = this.properties.isInvert();
            }

            String banner = imageBanner.printBanner(maxWidth, aspectRatio, invert);

            if(ansiOutput == true) {
                MutablePropertySources sources = new MutablePropertySources();
                sources.addFirst(new AnsiPropertySource("ansi", true));
                PropertyResolver ansiResolver = new PropertySourcesPropertyResolver(sources);
                banner = ansiResolver.resolvePlaceholders(banner);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(banner);
        } finally {
            if (image != null) {
                if (image.exists())
                    Assert.isTrue(image.delete(), String.format("couldn't delete temporary file %s",
                            image.getPath()));
            }
        }
    }

    private File imageFileFrom(MultipartFile file) throws Exception {
        Assert.notNull(file);
        Assert.isTrue(Arrays.asList(MEDIA_TYPES).contains(file.getContentType().toLowerCase()));
        File tmp = File.createTempFile("banner-tmp-",
                "." + file.getContentType().split("/")[1]);
        try (InputStream i = new BufferedInputStream(file.getInputStream());
             OutputStream o = new BufferedOutputStream(new FileOutputStream(tmp))) {
            FileCopyUtils.copy(i, o);
            return tmp;
        }
    }
}

@Data
@Component
@ConfigurationProperties(prefix = "banner")
class BannerProperties {

    private int maxWidth = 72;

    private double aspectRatio = 0.5;

    private boolean invert;

}

@Controller
class HomeController {

    @RequestMapping("/")
    public String index() {
        return "redirect:swagger-ui.html";
    }
}
