package ru.langsot.dating.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.langsot.dating.dto.Dtos.MatchDto;
import ru.langsot.dating.service.MatchingService;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchingService matchingService;

    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping
    public List<MatchDto> list(@CurrentUser Long userId) {
        return matchingService.getMatches(userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmatch(@CurrentUser Long userId, @PathVariable Long id) {
        matchingService.unmatch(userId, id);
    }
}
