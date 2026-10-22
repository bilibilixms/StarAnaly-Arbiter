package com.staranaly.arbiter.share;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/share/xiaohongshu")
public class XiaohongshuDraftController {

    private final XiaohongshuDraftService service;

    public XiaohongshuDraftController(XiaohongshuDraftService service) {
        this.service = service;
    }

    @PostMapping(path = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuDraftResponse generate(@RequestParam("image") MultipartFile image,
            @RequestParam("requirements") String requirements) {
        return service.generate(image, requirements);
    }
}
