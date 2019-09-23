package w.whateva.ocl2.job.integration.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.util.List;

/**
 * Created by rich on 10/15/15.
 */
@Setter
@Getter
public class XmlTeamWeek {

    private String header;
    private int points = 0;

    private List<XmlPlayerWeek> playerWeeks;

    @XmlElement(name = "header")
    public String getHeader() {
        return header;
    }

    @XmlElementWrapper(name = "players")
    @XmlElement(name = "player")
    public List<XmlPlayerWeek> getPlayerWeeks() {
        return playerWeeks;
    }

    public void init() {
        playerWeeks.forEach(XmlPlayerWeek::init);
    }
}
