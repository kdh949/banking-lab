package lab.banking.core.fds

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/staff/fds-cases")
class FdsCaseController(
    private val fdsCaseService: FdsCaseService
) {
    @GetMapping
    fun list(): List<FdsCaseDto> =
        fdsCaseService.list()

    @GetMapping("/{caseId}")
    fun case(@PathVariable caseId: String): FdsCaseDto =
        fdsCaseService.find(caseId)

    @PostMapping("/{caseId}/assign")
    fun assign(
        @PathVariable caseId: String,
        @RequestBody command: FdsAssignCommand
    ): FdsCaseDto =
        fdsCaseService.assign(caseId, command)

    @PostMapping("/{caseId}/release-requests")
    fun requestRelease(
        @PathVariable caseId: String,
        @RequestBody command: FdsDecisionCommand
    ): FdsDecisionRequestResponse =
        fdsCaseService.requestRelease(caseId, command)

    @PostMapping("/{caseId}/block-requests")
    fun requestBlock(
        @PathVariable caseId: String,
        @RequestBody command: FdsDecisionCommand
    ): FdsDecisionRequestResponse =
        fdsCaseService.requestBlock(caseId, command)
}
