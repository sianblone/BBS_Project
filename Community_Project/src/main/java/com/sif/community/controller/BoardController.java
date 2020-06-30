package com.sif.community.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.sif.community.model.BoardInfoVO;
import com.sif.community.model.BoardVO;
import com.sif.community.model.PaginationVO;
import com.sif.community.service.board.FileService;
import com.sif.community.service.board.itf.BoardService;
import com.sif.community.service.board.itf.PaginationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping(value = "/board")
@Controller
public class BoardController {
		
	@Autowired
	private FileService fileService;
	
	@Autowired
	@Qualifier(value = "pageSvc")
	private PaginationService pageSvc;
	
	@Autowired
	@Qualifier(value = "boardSvc")
	private BoardService boardSvc;
	
	// 게시판 컨트롤러
	// 리스트(GET) + 게시판 이름 + 검색 + 페이지 / 상세보기(GET) / 저장(GET: 화면 보여주기, POST: 입력 받기) / 삭제(POST)
	
	// 리스트 + 게시판 이름 + 검색 + 페이지 메소드
	// 검색 값과 현재 페이지로 페이지네이션 select하기
	@RequestMapping(value="/list", method=RequestMethod.GET)
	public String list(
					Model model,
					BoardVO boardVO,
					Integer currPage) {
		log.debug("INFO:{} TYPE:{} TXT:{}",boardVO.getBoard_info(), boardVO.getSearch_type(), boardVO.getSearch_txt());
		// 없는 게시판(0)을 입력받으면 메인페이지로
		if(boardVO.getBoard_info() == 0) return "redirect:/";
		
		if(boardVO.getSearch_type() == null) boardVO.setSearch_type("");
		if(boardVO.getSearch_txt() == null) boardVO.setSearch_txt("");
		
		// boardVO에는 게시판번호(board_info), 검색옵션(search_type), 검색어(search_txt)가 들어있다
		this.selectAllByPage(model, boardVO, currPage);
		
		return "board/list";
	}
	
	// 상세보기 메소드
	// id값으로 게시글 보여주기
	// boardOptionVO에는 board_info, board_no가 들어있다
	@RequestMapping(value="/details", method=RequestMethod.GET)
	public String details(BoardVO boardOptionVO, Model model, HttpServletRequest request, HttpServletResponse response) {
		BoardVO boardVO = boardSvc.findByBoardNo(boardOptionVO.getBoard_no());
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		
		// 현재 로그인한 사용자가 관리자가 아닐 때 delete 값이 1인 게시물 열람 불가
		if(!auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")) && boardVO.getBoard_delete() == 1) {
			return "board/error";
		}
		
		// 현재 로그인한 사용자 아이디와 작성자 아이디가 같거나, 로그인한 사용자 권한이 ADMIN일 때 글 수정,삭제 가능
		if( auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")) ) boardVO.setViewerAdmin(true);
		if( boardVO.getBoard_writer().equals(auth.getName()) ) boardVO.setViewerWriter(true);
		
		// 조회수 증가 (쿠키를 이용한 중복체크)
		int result = boardSvc.updateBoardCount(boardOptionVO, request, response);
		if(result > 0) {
			// 조회수가 증가되었다면 이미 DB에서 가져온 데이터의 조회수를 1 증가시켜 view에 보여주기
			boardVO.setBoard_count(boardVO.getBoard_count() + 1);
		}
		
		model.addAttribute("BOARD_VO",boardVO);
		
		return "board/details";
	}
	
	// 저장 메소드
	// boardOptionVO에는 board_no, board_info, board_p_no가 들어간다
	// board_no 값을 받지 않았으면 새 저장화면 보여주기(새 글 작성)
	// board_no 값을 받았으면 현재 사용자와 DB 게시글 작성자를 대조하여 일치하면 값 채워서 저장화면 보여주기(글 수정)
	// 현재 사용자와 DB의 게시글 작성자가 다르면 오류 페이지로 Redirect
	@RequestMapping(value="/save", method=RequestMethod.GET)
	public String save(BoardVO boardOptionVO, Model model) {
		// 없는 게시판(0)을 입력받으면 메인페이지로
		if(boardOptionVO.getBoard_info() == 0) return "redirect:/";
				
		String render = boardSvc.saveView(boardOptionVO, model);
		model.addAttribute("CATEGORY_LIST", boardSvc.selectCategoryByBoard(boardOptionVO));
		model.addAttribute("BOARD_INFO", boardSvc.findByBoardInfo(boardOptionVO.getBoard_info()));
		
		return render;
	}
	
	// form에서 저장버튼 클릭 시 사용할 메소드
	@RequestMapping(value="/save", method=RequestMethod.POST)
	public String save(BoardVO boardVO, Integer currPage) {
		
		String render = "";
		// 없는 게시판(0)을 입력받으면 메인페이지로
		if(boardVO.getBoard_info() == 0) {
			render = "redirect:/";
		} else {
			int result = boardSvc.save(boardVO);
			// 글 수정인 경우 DB에 board_no로 검색한 데이터가 없으면 에러페이지 보여주기
			if(result == -100) {
				render = "board/error";
			} else {
				render = "redirect:/board/list?board_info=" + boardVO.getBoard_info();
				if(currPage != null) render += "&currPage=" + currPage;
			}
		}
				
		return render;
	}
	
	// 게시물 삭제버튼 클릭 시 사용할 메소드
	// 게시글 deleted 칼럼 값 1로 바꿔주기
	@RequestMapping(value="/delete", method=RequestMethod.GET)
	public String delete(long board_no, Integer currPage) {
		String render = boardSvc.delete(board_no, currPage);
		return render;
	}
	
	// 추천버튼 클릭 시 사용할 메소드
	// 게시글 recommend 칼럼 값 +1
	// boardOptionVO에는 board_no가 들어있다
	@ResponseBody
	@RequestMapping(value="/recommend", method=RequestMethod.GET)
	public int recommend(BoardVO boardOptionVO, HttpServletRequest request, HttpServletResponse response) {
		int result = boardSvc.updateBoardRecommend(boardOptionVO, request, response);
		return result;
	}
	
	@RequestMapping(value = "/admin", method=RequestMethod.GET)
	public String admin(long board_no, Integer currPage, String order) {
		String render = boardSvc.admin(board_no, currPage, order);
		return render;
	}
	
	// 페이지네이션
	private void selectAllByPage(Model model, BoardVO boardVO, Integer currPage) {
		if(currPage == null) currPage = 1;
		
		log.debug("boardVO : {}", boardVO.toString());
		
		// 1. 페이징 할 곳에서 totalCount(총 데이터 수) 가져오기
		long totalCount = boardSvc.countAll(boardVO);
		log.debug("카운트 : {}", totalCount);
		
		// 2. 페이지네이션 정보 만들기
		PaginationVO pageVO = pageSvc.makePageInfoMiddle(totalCount, currPage);
		log.debug("페이지 : {}", pageVO.toString());
		
		// 3. 페이지네이션 정보 view로 보내주기
		model.addAttribute("PAGE_DTO", pageVO);
		
		// 4. 페이지네이션 기본 쿼리 view로 보내주기
		String page_default_query = "&board_info=" + boardVO.getBoard_info();
		model.addAttribute("PAGE_DEFAULT_QUERY", page_default_query);
		
		// 게시판 제목 표시
		BoardInfoVO boardInfoVO = boardSvc.findByBoardInfo(boardVO.getBoard_info());		
		model.addAttribute("BOARD_INFO", boardInfoVO);
		
		// 게시판 내용
		List<BoardVO> boardList = boardSvc.selectAllByPage(boardVO, pageVO);
		model.addAttribute("BOARD_LIST", boardList);
	}
	
	@ResponseBody
	@RequestMapping(value="/image_up", method = RequestMethod.POST, produces = "text/html;charset=UTF-8")
	public String fileUp(MultipartFile upFile) {
		
		log.debug("파일업:" + upFile.getOriginalFilename());
		
		String saveName = fileService.fileUp(upFile);
		if(saveName == null) {
			return "FAIL";
		}
		
		return saveName;
	}

}
