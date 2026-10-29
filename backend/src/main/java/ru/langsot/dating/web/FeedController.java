package ru.langsot.dating.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.langsot.dating.dto.Dtos.FeedCardDto;
import ru.langsot.dating.dto.Dtos.RateRequest;
import ru.langsot.dating.dto.Dtos.RateResult;
import ru.langsot.dating.service.FeedService;
import ru.langsot.dating.service.MatchingService;

import java.util.List;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;
    private final MatchingService matchingService;

    public FeedController(FeedService feedService, MatchingService matchingService) {
        this.feedService = feedService;
        this.matchingService = matchingService;
    }

    @GetMapping
    public List<FeedCardDto> getFeed(@CurrentUser Long userId, @RequestParam(required = false) Integer limit) {
        return feedService.getNextCards(userId, limit);
    }

    @PostMapping("/{targetUserId}/rate")
    public RateResult rate(@CurrentUser Long userId, @PathVariable Long targetUserId,
                           @Valid @RequestBody RateRequest req) {
        return matchingService.rate(userId, targetUserId, req.type());
    }
}
