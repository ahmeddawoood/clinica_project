package com.example.clinic.controller;

import com.example.clinic.domain.Message;
import com.example.clinic.domain.User;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.ResourceAccessDeniedException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.MessageRepository;
import com.example.clinic.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
public class MessagingController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;

    public MessagingController(MessageRepository messageRepository,
                                UserRepository userRepository,
                                DoctorRepository doctorRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
    }

    @GetMapping("/patient/messages")
    public String patientInbox(Model model, Principal principal) {
        User user = getUser(principal);
        List<Message> inbox = messageRepository.findByReceiverOrderBySentAtDesc(user);
        List<Message> sent  = messageRepository.findBySenderOrderBySentAtDesc(user);
        model.addAttribute("inbox", inbox);
        model.addAttribute("sent", sent);
        model.addAttribute("doctors", doctorRepository.findAll());
        model.addAttribute("unread", messageRepository.countUnread(user));
        model.addAttribute("section", "inbox");
        return "patient/messages";
    }

    @GetMapping("/patient/messages/{id}")
    @Transactional
    public String patientRead(@PathVariable Long id, Model model, Principal principal) {
        User user = getUser(principal);
        Message msg = messageRepository.findByIdWithSenderReceiver(id)
                .orElseThrow(() -> new ClinicException("Mesajul nu există"));
        if (!msg.getReceiver().getId().equals(user.getId())
                && !msg.getSender().getId().equals(user.getId())) {
            throw new ResourceAccessDeniedException("Nu aveți acces la acest mesaj.");
        }

        if (!msg.getReceiver().getId().equals(user.getId())
                && !msg.getSender().getId().equals(user.getId())) {
            throw new ResourceAccessDeniedException("Nu aveți acces la acest mesaj.");
        }
        if (msg.getReceiver().getId().equals(user.getId()) && !msg.isRead()) {
            msg.setRead(true);
            messageRepository.save(msg);
        }
        model.addAttribute("msg", msg);
        model.addAttribute("doctors", doctorRepository.findAll());
        model.addAttribute("section", "read");
        return "patient/messages";
    }

    @PostMapping("/patient/messages/send")
    @Transactional
    public String patientSend(@RequestParam Long receiverId,
                               @RequestParam String subject,
                               @RequestParam String body,
                               Principal principal, RedirectAttributes ra) {
        User sender   = getUser(principal);
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ClinicException("Destinatarul nu există"));
        Message msg = new Message();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setSubject(subject.isBlank() ? "(fără subiect)" : subject);
        msg.setBody(body);
        messageRepository.save(msg);
        ra.addFlashAttribute("success", "Mesaj trimis cu succes.");
        return "redirect:/patient/messages";
    }

    @GetMapping("/doctor/messages")
    public String doctorInbox(Model model, Principal principal) {
        User user = getUser(principal);
        List<Message> inbox = messageRepository.findByReceiverOrderBySentAtDesc(user);
        List<Message> sent  = messageRepository.findBySenderOrderBySentAtDesc(user);
        model.addAttribute("inbox", inbox);
        model.addAttribute("sent", sent);
        model.addAttribute("unread", messageRepository.countUnread(user));
        model.addAttribute("section", "inbox");
        return "doctor/messages";
    }

    @GetMapping("/doctor/messages/{id}")
    @Transactional
    public String doctorRead(@PathVariable Long id, Model model, Principal principal) {
        User user = getUser(principal);
        Message msg = messageRepository.findByIdWithSenderReceiver(id)
                .orElseThrow(() -> new ClinicException("Mesajul nu există"));
        if (msg.getReceiver().getId().equals(user.getId()) && !msg.isRead()) {
            msg.setRead(true);
            messageRepository.save(msg);
        }
        model.addAttribute("msg", msg);
        model.addAttribute("section", "read");
        return "doctor/messages";
    }

    @PostMapping("/doctor/messages/reply")
    @Transactional
    public String doctorReply(@RequestParam Long parentId,
                               @RequestParam String body,
                               Principal principal, RedirectAttributes ra) {
        User sender  = getUser(principal);
        Message parent = messageRepository.findById(parentId)
                .orElseThrow(() -> new ClinicException("Mesajul original nu există"));

        if (!parent.getReceiver().getId().equals(sender.getId())) {
            throw new ResourceAccessDeniedException("Nu aveți acces la acest mesaj.");
        }

        Message reply = new Message();
        reply.setSender(sender);
        reply.setReceiver(parent.getSender());
        reply.setSubject("Re: " + parent.getSubject());
        reply.setBody(body);
        reply.setParent(parent);
        messageRepository.save(reply);
        ra.addFlashAttribute("success", "Răspuns trimis.");
        return "redirect:/doctor/messages";
    }

    private User getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ClinicException("User negăsit: " + principal.getName()));
    }
}
