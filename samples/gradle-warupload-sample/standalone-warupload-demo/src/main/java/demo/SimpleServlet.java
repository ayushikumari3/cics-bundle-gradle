package demo;

import com.ibm.cics.server.InvalidRequestException;
import com.ibm.cics.server.Task;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet implementation class SimpleServlet
 */
@WebServlet("/SimpleServlet")
public class SimpleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");

        response.getWriter().print("Hello world!");
        Task task = Task.getTask();

        try {
            String userid = task.getUSERID();
            response.setContentType("text/html");
            response.getWriter().print("\nI am " + userid);
        } catch (InvalidRequestException e) {
            throw new RuntimeException(e);
        }
    }

}