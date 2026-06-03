package lab.banking.core.aml

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/staff/aml-cases")
class AmlCaseController(
    private val amlCaseService: AmlCaseService
) {
    @GetMapping
    fun list(): List<AmlCaseDto> =
        amlCaseService.list()

    @GetMapping("/{caseId}")
    fun case(@PathVariable caseId: String): AmlCaseDto =
        amlCaseService.find(caseId)

    @PostMapping("/{caseId}/assign")
    fun assign(
        @PathVariable caseId: String,
        @RequestBody command: AmlAssignCommand
    ): AmlCaseDto =
        amlCaseService.assign(caseId, command)

    @PostMapping("/{caseId}/comments")
    fun addComment(
        @PathVariable caseId: String,
        @RequestBody command: AmlCommentCommand
    ): AmlCaseDto =
        amlCaseService.addComment(caseId, command)

    @PostMapping("/{caseId}/closure-requests")
    fun requestClosure(
        @PathVariable caseId: String,
        @RequestBody command: AmlClosureCommand
    ): AmlClosureRequestResponse =
        amlCaseService.requestClosure(caseId, command)
}
