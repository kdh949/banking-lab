package lab.banking.core.journey

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customer/journeys")
class CustomerJourneyController(
    private val journeys: BusinessJourneyService
) {
    @GetMapping("/{journeyId}")
    fun journey(@PathVariable journeyId: String): CustomerJourneyDto =
        journeys.customerJourney(journeyId)
}

@RestController
@RequestMapping("/api/staff/journeys")
class StaffJourneyController(
    private val journeys: BusinessJourneyService
) {
    @GetMapping("/{journeyId}")
    fun journey(
        @PathVariable journeyId: String,
        @RequestParam(required = false) reason: String?
    ): StaffJourneyResponse = journeys.staffJourney(journeyId, reason)
}
