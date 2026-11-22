package com.mujian.backend.prop.service;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.prop.dto.PropBrowseQueryDTO;
import com.mujian.backend.prop.vo.PropCardVO;
import com.mujian.backend.prop.vo.RentableCardVO;

public interface PropBrowseService {
  /** 查询公开道具列表并批量组装展示数据。 */
  PageResult<PropCardVO> browse(PropBrowseQueryDTO propBrowseQueryDTO);

  /** 校验租赁方身份后查询匹配道具。 */
  PageResult<PropCardVO> search(String actor, PropBrowseQueryDTO propBrowseQueryDTO);

  /** 计算全体候选的租期可租量，完成全局排序后分页。 */
  PageResult<RentableCardVO> rentable(String actor, PropBrowseQueryDTO propBrowseQueryDTO);
}
